package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.BlockDownloader
import com.mobicloud.domain.repository.PeerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
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
        // 1. Guard — locations insuffisantes pour atteindre k.
        if (blockMap.size < k) {
            send(DownloadProgressState.Failed(
                reason = "Insuffisant : ${blockMap.size}/$k blocs localisés",
                received = 0,
                k = k
            ))
            return@channelFlow
        }

        // 2. Pool K+2 (clamp sur blockMap.size). Priorité aux pairs les plus fiables.
        val poolSize = minOf(blockMap.size, k + 2)
        val locations = blockMap.values
            .sortedByDescending { it.reliabilityScore }
            .take(poolSize)

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

        coroutineScope {
            val jobs = locations.map { loc ->
                launch {
                    try {
                        var result = blockDownloader.downloadBlock(loc, BASE_ACK_TIMEOUT_MS)
                        if (result.isFailure) {
                            // AC#4 / AC#6 — fallback : 1er pair actif jamais utilisé pour ce round.
                            val fallback = activePeers.firstOrNull { p ->
                                usedNodeIds.add(p.identity.nodeId)
                            }
                            if (fallback != null) {
                                val fallbackLoc = loc.copy(
                                    nodeId = fallback.identity.nodeId,
                                    ipAddress = fallback.ipAddress!!,
                                    port = fallback.port!!,
                                    reliabilityScore = fallback.identity.reliabilityScore
                                )
                                result = blockDownloader.downloadBlock(fallbackLoc, MAX_ACK_TIMEOUT_MS)
                            }
                        }
                        results.trySend(DownloadResult(loc.fragmentIndex, result))
                    } catch (e: CancellationException) {
                        // AC#3 — l'annulation des jobs perdants est volontaire ; ne pas la traiter
                        // comme un échec, juste laisser propager.
                        throw e
                    } catch (e: Exception) {
                        results.trySend(DownloadResult(loc.fragmentIndex, Result.failure(e)))
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
                        send(DownloadProgressState.Progress(completed.size, k, failedCount))
                    }
                }.onFailure {
                    failedCount++
                    send(DownloadProgressState.Progress(completed.size, k, failedCount))
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
        val result: Result<DownloadedBlock>
    )

    companion object {
        const val BASE_ACK_TIMEOUT_MS = 10_000L
        const val MAX_ACK_TIMEOUT_MS = 30_000L
    }
}
