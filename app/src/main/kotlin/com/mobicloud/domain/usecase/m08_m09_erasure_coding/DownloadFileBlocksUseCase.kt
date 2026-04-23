package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.BlockDownloader
import com.mobicloud.domain.repository.PeerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 6.2 — orchestrateur de téléchargement K+2 compétitif.
 *
 * Stratégie :
 * 1. Sélectionne un pool de `min(blockMap.size, k+2)` locations triées par fiabilité.
 * 2. Lance un job parallèle par location ; chaque échec primaire déclenche un retry contre
 *    un pair actif de fallback (timeout étendu).
 * 3. Le consumer collecte les résultats au fil de l'eau via un `Channel` (pas de polling).
 *    Dès que K blocs uniques (par `fragmentIndex`) sont accumulés, les jobs perdants sont
 *    annulés via la structured concurrency de `coroutineScope`.
 * 4. Émet `Progress(received, k, failed)` à chaque événement, puis `Completed(blocks)`
 *    ou `Failed(reason)` à la sortie.
 */
@Singleton
class DownloadFileBlocksUseCase @Inject constructor(
    private val blockDownloader: BlockDownloader,
    private val peerRepository: PeerRepository
) {

    fun invoke(
        blockMap: Map<String, ResolvedBlockLocation>,
        k: Int
    ): Flow<DownloadProgressState> = channelFlow {
        // 1. Guard — [Review][Patch] P4 — k <= 0 serait un bug d'appel (ErasureParameters default
        // positif), mais se protéger évite une loop `while (0 < 0)` silencieuse qui émettrait
        // Completed(emptyMap) sur k=0 ou un comportement indéfini sur k négatif.
        if (k <= 0) {
            send(DownloadProgressState.Failed(
                reason = "k invalide : $k",
                received = 0,
                k = k
            ))
            return@channelFlow
        }
        if (blockMap.size < k) {
            send(DownloadProgressState.Failed(
                reason = "Insuffisant : ${blockMap.size}/$k blocs localisés",
                received = 0,
                k = k
            ))
            return@channelFlow
        }

        // 2. Pool K+2 (clamp sur blockMap.size). [Review][Patch] Priorité à la couverture
        // fragmentIndex AVANT le tri par fiabilité : on prend la meilleure réplique de chaque
        // fragment, puis on complète avec des répliques excédentaires triées par fiabilité.
        // Garantit que, si les répliques existent, les K fragments distincts sont atteignables.
        val poolSize = minOf(blockMap.size, k + 2)
        val byFragment = blockMap.values.groupBy { it.fragmentIndex }
        val primaries = byFragment.values
            .map { replicas -> replicas.maxByOrNull { it.reliabilityScore }!! }
            .sortedByDescending { it.reliabilityScore }
        val extras = blockMap.values
            .filter { loc -> primaries.none { it == loc } }
            .sortedByDescending { it.reliabilityScore }
        val locations = (primaries + extras).take(poolSize)

        // 3. Snapshot unique des pairs actifs pour fallback (pattern 6.1 / 5.3).
        val activePeers = peerRepository.peers.value
            .filter { it.isActive && it.ipAddress != null && it.port != null }

        // 4. Channel consumer-driven (pas de polling). Capacité = jobs.size garantit que
        //    `trySend` ne peut jamais échouer pour cause de buffer plein.
        val results = Channel<DownloadResult>(capacity = locations.size)
        val completed = LinkedHashMap<Int, DownloadedBlock>()
        val usedNodeIds = ConcurrentHashMap.newKeySet<String>().apply {
            addAll(locations.map { it.nodeId })
        }
        var failedCount = 0
        val contributions = Collections.synchronizedList(mutableListOf<DownloadProgressState.BlockContribution>())
        val slowNodeIds = ConcurrentHashMap.newKeySet<String>()
        val failedFragmentIndices = ConcurrentHashMap.newKeySet<Int>()

        coroutineScope {
            val jobs = locations.map { loc ->
                launch {
                    val slowJob = launch {
                        delay(SLOW_THRESHOLD_MS)
                        slowNodeIds.add(loc.nodeId)
                    }
                    try {
                        var result = blockDownloader.downloadBlock(loc, BASE_ACK_TIMEOUT_MS)
                        var effectiveNodeId = loc.nodeId
                        var isFallback = false
                        if (result.isFailure) {
                            // AC#4 / AC#6 — fallback : 1er pair actif jamais utilisé pour ce round.
                            // [Review][Patch] Boucle explicite : `usedNodeIds.add` a un side-effect,
                            // l'isoler d'un prédicat de filtre évite les surprises.
                            var fallback: Peer? = null
                            for (p in activePeers) {
                                if (usedNodeIds.add(p.identity.nodeId)) {
                                    fallback = p
                                    break
                                }
                            }
                            if (fallback != null) {
                                val fallbackLoc = loc.copy(
                                    nodeId = fallback.identity.nodeId,
                                    ipAddress = fallback.ipAddress!!,
                                    port = fallback.port!!,
                                    reliabilityScore = fallback.identity.reliabilityScore
                                )
                                result = blockDownloader.downloadBlock(fallbackLoc, MAX_ACK_TIMEOUT_MS)
                                effectiveNodeId = fallback.identity.nodeId
                                isFallback = true
                            }
                        }
                        results.trySend(DownloadResult(loc.fragmentIndex, result, effectiveNodeId, isFallback))
                    } catch (e: CancellationException) {
                        // AC#3 — l'annulation des jobs perdants est volontaire ; ne pas la traiter
                        // comme un échec, juste laisser propager.
                        throw e
                    } catch (e: Exception) {
                        results.trySend(DownloadResult(loc.fragmentIndex, Result.failure(e)))
                    } finally {
                        slowJob.cancel()
                    }
                }
            }

            var remaining = jobs.size
            while (completed.size < k && remaining > 0) {
                val dr = results.receive()
                remaining--
                dr.result.onSuccess { block ->
                    // AC#3 + contrainte 6 — dédupe par fragmentIndex (cas réplique).
                    if (completed.putIfAbsent(block.fragmentIndex, block) == null) {
                        contributions.add(
                            DownloadProgressState.BlockContribution(
                                nodeId = dr.nodeId,
                                fragmentIndex = block.fragmentIndex,
                                latencyMs = block.latencyMs,
                                isFallback = dr.isFallback
                            )
                        )
                        send(DownloadProgressState.Progress(
                            completed.size, k, failedCount,
                            contributions.toList(), slowNodeIds.toSet(),
                            failedFragmentIndices.toSet()
                        ))
                    }
                }.onFailure {
                    failedCount++
                    failedFragmentIndices.add(dr.fragmentIndex)
                    send(DownloadProgressState.Progress(
                        completed.size, k, failedCount,
                        contributions.toList(), slowNodeIds.toSet(),
                        failedFragmentIndices.toSet()
                    ))
                }
            }

            // AC#3 — K atteint : annuler les jobs perdants. coroutineScope attend leur sortie.
            jobs.forEach { it.cancel() }
        }

        if (completed.size >= k) {
            send(DownloadProgressState.Completed(completed.toMap()))
        } else {
            send(DownloadProgressState.Failed(
                reason = "Seulement ${completed.size}/$k blocs valides",
                received = completed.size,
                k = k
            ))
        }
    }

    private data class DownloadResult(
        val fragmentIndex: Int,
        val result: Result<DownloadedBlock>,
        val nodeId: String = "",
        val isFallback: Boolean = false
    )

    companion object {
        const val BASE_ACK_TIMEOUT_MS = 10_000L
        const val MAX_ACK_TIMEOUT_MS = 30_000L
        const val SLOW_THRESHOLD_MS = 5_000L
    }
}
