package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.gossip.BloomDigest
import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse
import com.mobicloud.domain.models.gossip.DhtEntryDto
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.ResolveDhtConflictUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gossip anti-entropy du catalogue DHT (protocole v1, push-pull).
 *
 * Round initié par A vers B (fan-out fixe = 2 voisins aléatoires par cycle) :
 *  1. A → B : BLOOM(A), filtre dimensionné pour fp ≈ 1 %, salé aléatoirement à ce round.
 *  2. B calcule D(A) = entrées de B absentes de BLOOM(A), c.-à-d. ce qui manque à A.
 *  3. B → A : DELTA_REQ { push = D(A), résumé de B }.
 *     Le résumé de B est un Bloom salé, ou tous les [ANTI_ENTROPY_EVERY] rounds la liste
 *     exacte de ses clés (anti-entropy : rattrape tout faux positif résiduel).
 *  4. A fusionne D(A) (CRDT LWW), calcule D(B) = entrées de A absentes du résumé de B.
 *  5. A → B : DELTA_RESP { D(B) } ; B fusionne.
 *
 * Les éléments du filtre sont des clés versionnées (blockId|timestamp|nodeId) : une mise à
 * jour d'entrée (timestamp plus récent) est donc vue comme manquante et se propage, ce que
 * ne faisait pas un filtre indexé sur le seul blockId.
 *
 * Les messages v0 (anciennes versions) suivent l'ancien chemin, inchangé.
 */
@Singleton
class GossipSyncUseCase @Inject constructor(
    private val dhtRepository: DhtRepository,
    private val peerRepository: PeerRepository,
    private val gossipOutboundPort: GossipOutboundPort,
    private val networkEventRepository: NetworkEventRepository,
    private val securityRepository: SecurityRepository,
    private val resolveDhtConflictUseCase: ResolveDhtConflictUseCase,
    @ApplicationScope private val scope: CoroutineScope
) : GossipIncomingHandler {

    companion object {
        /** Fan-out fixe (choix énergétique, cf. évaluation). */
        const val FAN_OUT = 2
        private const val DELTA_REQUEST_TIMEOUT_MS = 3000L

        const val PROTOCOL_VERSION = 1

        /** Un DELTA_REQ sur [ANTI_ENTROPY_EVERY] transporte la liste exacte des clés. */
        const val ANTI_ENTROPY_EVERY = 10L

        /** Au-delà, la liste exacte dépasserait la limite de taille : on garde un Bloom à fp très bas. */
        const val MAX_EXACT_KEYS = 4000
        private const val FALLBACK_FP_RATE = 0.001

        /** Borne anti-abus du nombre d'entrées poussées acceptées par requête. */
        const val MAX_PUSHED_ENTRIES = 5000

        /** Clé versionnée d'une entrée : un changement de version la rend « manquante » chez le pair. */
        fun versionKey(entry: DhtEntry): String = "${entry.blockId}|${entry.timestamp}|${entry.nodeId}"
    }

    private val secureRandom = SecureRandom()
    private val incomingRounds = AtomicLong(0)

    suspend fun runGossipCycle(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val allPeers = peerRepository.peers.value
            val activePeers = allPeers.filter { it.isActive }
            android.util.Log.i("MobiCloud:Gossip", "[DIAG] runGossipCycle — total=${allPeers.size} actifs=${activePeers.size} ${activePeers.map { "${it.identity.nodeId.take(8)}@${it.ipAddress}:${it.port}" }}")

            // Guard N=0 : cluster isolé, aucune action
            if (activePeers.isEmpty()) return@withContext Result.success(Unit)

            val localEntries = dhtRepository.observeAllEntries().first()
            val localBloom = buildBloom(localEntries, nextSalt())

            // F9: résolution de l'identité locale via SecurityRepository (pas depuis les pairs distants)
            val localNodeId = localNodeId()
            val gossipMsg = BloomFilterGossip(
                senderNodeId = localNodeId,
                bloomFilterBytes = localBloom.toByteArray(),
                bloomFilterSize = localBloom.bitArraySize,
                numHashFunctions = localBloom.numHashFunctions,
                // v1 : la liste complète n'est plus envoyée (elle annulait le gain du filtre
                // et n'était lue par aucun récepteur).
                partitionIds = emptyList(),
                timestamp = System.currentTimeMillis(),
                salt = localBloom.salt,
                protocolVersion = PROTOCOL_VERSION
            )

            // F3: fan-out best-effort — continuer même si un pair est injoignable
            val neighbors = selectRandomNeighbors(activePeers, FAN_OUT)
            var lastFailure: Throwable? = null
            for (peer in neighbors) {
                val nodeId = peer.identity.nodeId
                android.util.Log.i("MobiCloud:Gossip", "[DIAG] sendBloomGossip → ${nodeId.take(8)}")
                gossipOutboundPort.sendBloomGossip(nodeId, gossipMsg)
                    .onSuccess {
                        android.util.Log.i("MobiCloud:Gossip", "[DIAG] sendBloomGossip OK → ${nodeId.take(8)}")
                    }
                    .onFailure { e ->
                        android.util.Log.w("MobiCloud:Gossip", "[DIAG] sendBloomGossip FAIL → ${nodeId.take(8)} : ${e.message}")
                        networkEventRepository.pushEvent("[GOSSIP] Envoi Bloom échoué → ${nodeId.take(8)}")
                        lastFailure = e
                    }
            }
            if (lastFailure != null) Result.failure(lastFailure!!) else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun onBloomGossipReceived(msg: BloomFilterGossip) {
        scope.launch { handleIncomingBloom(msg) }
    }

    override fun onDeltaSyncRequestReceived(req: DeltaSyncRequest): DeltaSyncResponse? {
        return runBlocking {
            withTimeoutOrNull(DELTA_REQUEST_TIMEOUT_MS) { handleDeltaRequest(req).getOrNull() }
        }
    }

    suspend fun handleIncomingBloom(
        msg: BloomFilterGossip
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            // Hardening anti-amplification : un Bloom forge declenche un DeltaSyncRequest
            // sortant. Sans ce filtre, un attaquant non-pair pouvait amplifier le trafic
            // en spammant des Bloom, forcant la victime a consulter sa DHT et a emettre.
            if (!isKnownActivePeer(msg.senderNodeId)) {
                networkEventRepository.pushEvent(
                    "[GOSSIP] Bloom rejete : sender ${msg.senderNodeId.take(8)} non-pair"
                )
                return@withContext Result.success(Unit)
            }

            if (msg.protocolVersion >= 1) {
                handleIncomingBloomV1(msg)
            } else {
                handleIncomingBloomLegacy(msg)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun handleIncomingBloomV1(msg: BloomFilterGossip) {
        val remoteBloom = BloomFilter.fromByteArray(
            msg.bloomFilterBytes, msg.bloomFilterSize, msg.numHashFunctions, msg.salt
        )
        val localEntries = dhtRepository.observeAllEntries().first()

        // Push : ce que l'émetteur n'a pas (ou a dans une version différente)
        val toPush = localEntries.filter { !remoteBloom.mightContain(versionKey(it)) }

        val antiEntropy = incomingRounds.incrementAndGet() % ANTI_ENTROPY_EVERY == 0L &&
            localEntries.size <= MAX_EXACT_KEYS

        // Raccourci : rien à pousser et filtres identiques (mêmes paramètres) → catalogues
        // quasi certainement égaux. Un écart masqué sera rattrapé à un round suivant (autre
        // sel) ou au prochain round d'anti-entropy, qui n'utilise jamais ce raccourci.
        if (!antiEntropy && toPush.isEmpty() && sameBloom(localEntries, msg)) {
            android.util.Log.i("MobiCloud:Gossip", "[DIAG] en phase avec ${msg.senderNodeId.take(8)} — aucun échange")
            return
        }

        val req = if (antiEntropy) {
            DeltaSyncRequest(
                requesterNodeId = localNodeId(),
                missingBlockIds = emptyList(),
                timestamp = System.currentTimeMillis(),
                pushedEntries = toPush.map { it.toDto() },
                requesterKnownKeys = localEntries.map { versionKey(it) },
                antiEntropy = true
            )
        } else {
            val fp = if (localEntries.size > MAX_EXACT_KEYS) FALLBACK_FP_RATE else BloomFilter.DEFAULT_TARGET_FP_RATE
            val digest = buildBloom(localEntries, nextSalt(), fp)
            DeltaSyncRequest(
                requesterNodeId = localNodeId(),
                missingBlockIds = emptyList(),
                timestamp = System.currentTimeMillis(),
                pushedEntries = toPush.map { it.toDto() },
                requesterDigest = BloomDigest(
                    bytes = digest.toByteArray(),
                    size = digest.bitArraySize,
                    numHashFunctions = digest.numHashFunctions,
                    salt = digest.salt
                )
            )
        }

        networkEventRepository.pushEvent(
            "[GOSSIP] Push-pull → ${msg.senderNodeId.take(8)} : ${toPush.size} entrées poussées" +
                if (antiEntropy) " (anti-entropy)" else ""
        )
        gossipOutboundPort.sendDeltaSyncRequest(msg.senderNodeId, req)
            .onSuccess { response -> handleDeltaResponse(response) }
            .onFailure {
                networkEventRepository.pushEvent("[GOSSIP] DeltaSync échoué vers ${msg.senderNodeId.take(8)}")
            }
    }

    /** Chemin v0, conservé à l'identique pour les messages des anciennes versions. */
    private suspend fun handleIncomingBloomLegacy(msg: BloomFilterGossip) {
        val remoteBloom = BloomFilter.fromByteArray(
            msg.bloomFilterBytes,
            msg.bloomFilterSize,
            msg.numHashFunctions
        )
        val localEntries = dhtRepository.observeAllEntries().first()
        val potentiallyMissing = localEntries
            .filter { !remoteBloom.mightContain(it.blockId) }
            .map { it.blockId }

        if (potentiallyMissing.isNotEmpty()) {
            networkEventRepository.pushEvent(
                "[GOSSIP] Delta détecté : ${potentiallyMissing.size} entrées manquantes chez ${msg.senderNodeId.take(8)}"
            )
            val req = DeltaSyncRequest(
                requesterNodeId = localNodeId(),
                missingBlockIds = potentiallyMissing,
                timestamp = System.currentTimeMillis()
            )
            gossipOutboundPort.sendDeltaSyncRequest(msg.senderNodeId, req)
                .onSuccess { response -> handleDeltaResponse(response) }
                .onFailure {
                    networkEventRepository.pushEvent("[GOSSIP] DeltaSync échoué vers ${msg.senderNodeId.take(8)}")
                }
        }
    }

    suspend fun handleDeltaRequest(req: DeltaSyncRequest): Result<DeltaSyncResponse> =
        withContext(Dispatchers.Default) {
            try {
                // Hardening anti-DoS : seuls les pairs connus peuvent demander des
                // entrees DHT. Sans ce filtre, n'importe qui pouvait spammer des
                // missingBlockIds pour saturer notre DB.
                if (!isKnownActivePeer(req.requesterNodeId)) {
                    networkEventRepository.pushEvent(
                        "[GOSSIP] DeltaRequest rejete : requester ${req.requesterNodeId.take(8)} non-pair"
                    )
                    return@withContext Result.failure(
                        SecurityException("DeltaRequest from unknown peer ${req.requesterNodeId}")
                    )
                }

                val dtos = if (req.isV1) answerV1(req) else answerLegacy(req)

                // F9: identité locale via SecurityRepository
                Result.success(
                    DeltaSyncResponse(
                        responderNodeId = localNodeId(),
                        entries = dtos,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** v1 : fusion des entrées poussées, puis renvoi de ce qui manque au demandeur. */
    private suspend fun answerV1(req: DeltaSyncRequest): List<DhtEntryDto> {
        if (req.pushedEntries.size > MAX_PUSHED_ENTRIES) {
            throw IllegalArgumentException("too many pushed entries: ${req.pushedEntries.size}")
        }
        mergeEntries(req.pushedEntries)

        // Instantané pris après fusion : évite de renvoyer une version que l'on vient de remplacer.
        val localEntries = dhtRepository.observeAllEntries().first()
        val missing = if (req.antiEntropy) {
            val known = req.requesterKnownKeys.toHashSet()
            localEntries.filter { versionKey(it) !in known }
        } else {
            val d = req.requesterDigest ?: return emptyList()
            val bloom = BloomFilter.fromByteArray(d.bytes, d.size, d.numHashFunctions, d.salt)
            localEntries.filter { !bloom.mightContain(versionKey(it)) }
        }
        networkEventRepository.pushEvent(
            "[GOSSIP] Push-pull ← ${req.requesterNodeId.take(8)} : ${req.pushedEntries.size} reçues, ${missing.size} renvoyées" +
                if (req.antiEntropy) " (anti-entropy)" else ""
        )
        return missing.map { it.toDto() }
    }

    private suspend fun answerLegacy(req: DeltaSyncRequest): List<DhtEntryDto> =
        req.missingBlockIds.mapNotNull { blockId ->
            dhtRepository.findByBlockId(blockId).getOrNull()
        }.map { it.toDto() }

    suspend fun handleDeltaResponse(response: DeltaSyncResponse): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                // Hardening anti-DHT-poisoning : seuls les pairs connus peuvent injecter
                // des entrees DHT. Sans ce filtre, un attaquant non-pair pouvait empoisonner
                // notre DHT avec des fausses routes (blockId X chez IP-attaquant).
                if (!isKnownActivePeer(response.responderNodeId)) {
                    networkEventRepository.pushEvent(
                        "[GOSSIP] DeltaResponse rejete : responder ${response.responderNodeId.take(8)} non-pair"
                    )
                    return@withContext Result.failure(
                        SecurityException("DeltaResponse from unknown peer ${response.responderNodeId}")
                    )
                }

                mergeEntries(response.entries)
                networkEventRepository.pushEvent(
                    "[GOSSIP] Convergence : ${response.entries.size} entrées reçues de ${response.responderNodeId.take(8)}"
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    internal fun selectRandomNeighbors(peers: List<com.mobicloud.domain.models.Peer>, count: Int = FAN_OUT): List<com.mobicloud.domain.models.Peer> {
        return peers.shuffled().take(count)
    }

    private suspend fun mergeEntries(dtos: List<DhtEntryDto>) {
        for (dto in dtos) {
            resolveDhtConflictUseCase.resolve(dto.toEntry())
                .onFailure { networkEventRepository.pushEvent("[CRDT] Résolution échouée pour ${dto.blockId.take(8)} : ${it.message}") }
        }
    }

    private fun buildBloom(
        entries: List<DhtEntry>,
        salt: Long,
        fpRate: Double = BloomFilter.DEFAULT_TARGET_FP_RATE
    ): BloomFilter {
        val filter = BloomFilter.sized(entries.size, fpRate, salt)
        entries.forEach { filter.add(versionKey(it)) }
        return filter
    }

    /** Vrai si notre catalogue, haché avec les paramètres du pair, donne exactement son filtre. */
    private fun sameBloom(localEntries: List<DhtEntry>, msg: BloomFilterGossip): Boolean {
        val mine = BloomFilter(msg.bloomFilterSize, msg.numHashFunctions, msg.salt)
        localEntries.forEach { mine.add(versionKey(it)) }
        val theirs = BloomFilter.fromByteArray(
            msg.bloomFilterBytes, msg.bloomFilterSize, msg.numHashFunctions, msg.salt
        )
        return mine.toByteArray().contentEquals(theirs.toByteArray())
    }

    /** Sel non nul (0 est réservé au hachage legacy). */
    private fun nextSalt(): Long {
        var s: Long
        do { s = secureRandom.nextLong() } while (s == 0L)
        return s
    }

    private fun isKnownActivePeer(nodeId: String): Boolean =
        peerRepository.peers.value.any { it.identity.nodeId == nodeId && it.isActive }

    private suspend fun localNodeId(): String =
        securityRepository.getIdentity().getOrNull()?.nodeId ?: "local"

    private fun DhtEntry.toDto() = DhtEntryDto(
        blockId = blockId, nodeId = nodeId, ipAddress = ipAddress, port = port, timestamp = timestamp
    )

    private fun DhtEntryDto.toEntry() = DhtEntry(
        blockId = blockId, nodeId = nodeId, ipAddress = ipAddress, port = port, timestamp = timestamp
    )
}
