package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.DhtEntry
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
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

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
        private const val FAN_OUT = 2
        private const val DELTA_REQUEST_TIMEOUT_MS = 3000L
    }

    suspend fun runGossipCycle(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val allPeers = peerRepository.peers.value
            val activePeers = allPeers.filter { it.isActive }
            android.util.Log.i("MobiCloud:Gossip", "[DIAG] runGossipCycle — total=${allPeers.size} actifs=${activePeers.size} ${activePeers.map { "${it.identity.nodeId.take(8)}@${it.ipAddress}:${it.port}" }}")

            // Guard N=0 : cluster isolé, aucune action
            if (activePeers.isEmpty()) return@withContext Result.success(Unit)

            val localEntries = dhtRepository.observeAllEntries().first()
            val localBloom = buildLocalBloomFilter(localEntries)
            val bloomBytes = localBloom.toByteArray()
            val partitionIds = localEntries.map { it.blockId }.distinct()

            // F9: résolution de l'identité locale via SecurityRepository (pas depuis les pairs distants)
            val localNodeId = securityRepository.getIdentity().getOrNull()?.nodeId ?: "local"
            val gossipMsg = BloomFilterGossip(
                senderNodeId = localNodeId,
                bloomFilterBytes = bloomBytes,
                bloomFilterSize = localBloom.bitArraySize,
                numHashFunctions = localBloom.numHashFunctions,
                partitionIds = partitionIds,
                timestamp = System.currentTimeMillis()
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
            withTimeout(DELTA_REQUEST_TIMEOUT_MS) { handleDeltaRequest(req).getOrNull() }
        }
    }

    suspend fun handleIncomingBloom(
        msg: BloomFilterGossip
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            // Hardening anti-amplification : un Bloom forge declenche un DeltaSyncRequest
            // sortant. Sans ce filtre, un attaquant non-pair pouvait amplifier le trafic
            // en spammant des Bloom, forcant la victime a consulter sa DHT et a emettre.
            val isKnownPeer = peerRepository.peers.value
                .any { it.identity.nodeId == msg.senderNodeId && it.isActive }
            if (!isKnownPeer) {
                networkEventRepository.pushEvent(
                    "[GOSSIP] Bloom rejete : sender ${msg.senderNodeId.take(8)} non-pair"
                )
                return@withContext Result.success(Unit)
            }

            val remoteBloom = BloomFilter.fromByteArray(
                msg.bloomFilterBytes,
                msg.bloomFilterSize,
                msg.numHashFunctions
            )
            val localEntries = dhtRepository.observeAllEntries().first()
            val potentiallyMissing = computeDelta(localEntries, remoteBloom)

            if (potentiallyMissing.isNotEmpty()) {
                networkEventRepository.pushEvent(
                    "[GOSSIP] Delta détecté : ${potentiallyMissing.size} entrées manquantes chez ${msg.senderNodeId.take(8)}"
                )
                val localNodeId = securityRepository.getIdentity().getOrNull()?.nodeId ?: "local"
                val req = DeltaSyncRequest(
                    requesterNodeId = localNodeId,
                    missingBlockIds = potentiallyMissing,
                    timestamp = System.currentTimeMillis()
                )
                gossipOutboundPort.sendDeltaSyncRequest(msg.senderNodeId, req)
                    .onSuccess { response -> handleDeltaResponse(response) }
                    .onFailure {
                        networkEventRepository.pushEvent("[GOSSIP] DeltaSync échoué vers ${msg.senderNodeId.take(8)}")
                    }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun handleDeltaRequest(req: DeltaSyncRequest): Result<DeltaSyncResponse> =
        withContext(Dispatchers.Default) {
            try {
                // Hardening anti-DoS : seuls les pairs connus peuvent demander des
                // entrees DHT. Sans ce filtre, n'importe qui pouvait spammer des
                // missingBlockIds pour saturer notre DB.
                val isKnownPeer = peerRepository.peers.value
                    .any { it.identity.nodeId == req.requesterNodeId && it.isActive }
                if (!isKnownPeer) {
                    networkEventRepository.pushEvent(
                        "[GOSSIP] DeltaRequest rejete : requester ${req.requesterNodeId.take(8)} non-pair"
                    )
                    return@withContext Result.failure(
                        SecurityException("DeltaRequest from unknown peer ${req.requesterNodeId}")
                    )
                }

                val entries = req.missingBlockIds.mapNotNull { blockId ->
                    dhtRepository.findByBlockId(blockId).getOrNull()
                }
                val dtos = entries.map { entry ->
                    DhtEntryDto(
                        blockId = entry.blockId,
                        nodeId = entry.nodeId,
                        ipAddress = entry.ipAddress,
                        port = entry.port,
                        timestamp = entry.timestamp
                    )
                }
                // F9: identité locale via SecurityRepository
                val localNodeId = securityRepository.getIdentity().getOrNull()?.nodeId ?: "local"
                Result.success(
                    DeltaSyncResponse(
                        responderNodeId = localNodeId,
                        entries = dtos,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun handleDeltaResponse(response: DeltaSyncResponse): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                // Hardening anti-DHT-poisoning : seuls les pairs connus peuvent injecter
                // des entrees DHT. Sans ce filtre, un attaquant non-pair pouvait empoisonner
                // notre DHT avec des fausses routes (blockId X chez IP-attaquant).
                val isKnownPeer = peerRepository.peers.value
                    .any { it.identity.nodeId == response.responderNodeId && it.isActive }
                if (!isKnownPeer) {
                    networkEventRepository.pushEvent(
                        "[GOSSIP] DeltaResponse rejete : responder ${response.responderNodeId.take(8)} non-pair"
                    )
                    return@withContext Result.failure(
                        SecurityException("DeltaResponse from unknown peer ${response.responderNodeId}")
                    )
                }

                for (dto in response.entries) {
                    val entry = DhtEntry(
                        blockId = dto.blockId,
                        nodeId = dto.nodeId,
                        ipAddress = dto.ipAddress,
                        port = dto.port,
                        timestamp = dto.timestamp
                    )
                    resolveDhtConflictUseCase.resolve(entry)
                        .onFailure { networkEventRepository.pushEvent("[CRDT] Résolution échouée pour ${dto.blockId.take(8)} : ${it.message}") }
                }
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

    private suspend fun buildLocalBloomFilter(entries: List<DhtEntry>): BloomFilter {
        val filter = BloomFilter()
        entries.forEach { filter.add(it.blockId) }
        return filter
    }

    private fun computeDelta(localEntries: List<DhtEntry>, remoteBloom: BloomFilter): List<String> {
        return localEntries
            .filter { !remoteBloom.mightContain(it.blockId) }
            .map { it.blockId }
    }
}
