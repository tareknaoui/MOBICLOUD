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
                val ip = peer.ipAddress ?: continue
                val port = peer.port ?: continue
                android.util.Log.i("MobiCloud:Gossip", "[DIAG] sendBloomGossip → ${peer.identity.nodeId.take(8)}@$ip:$port")
                gossipOutboundPort.sendBloomGossip(ip, port, gossipMsg)
                    .onSuccess {
                        android.util.Log.i("MobiCloud:Gossip", "[DIAG] sendBloomGossip OK → ${peer.identity.nodeId.take(8)}@$ip:$port")
                    }
                    .onFailure { e ->
                        android.util.Log.w("MobiCloud:Gossip", "[DIAG] sendBloomGossip FAIL → ${peer.identity.nodeId.take(8)}@$ip:$port : ${e.message}")
                        networkEventRepository.pushEvent("[GOSSIP] Envoi Bloom échoué → ${peer.identity.nodeId.take(8)}")
                        lastFailure = e
                    }
            }
            if (lastFailure != null) Result.failure(lastFailure!!) else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // F1: fire-and-forget via ApplicationScope — ne bloque plus le thread accept TCP
    override fun onBloomGossipReceived(msg: BloomFilterGossip, senderIp: String, senderPort: Int) {
        scope.launch { handleIncomingBloom(msg, senderIp, senderPort) }
    }

    // F1: timeout 3s pour éviter un blocage indéfini du thread accept TCP
    override fun onDeltaSyncRequestReceived(req: DeltaSyncRequest): DeltaSyncResponse? {
        return runBlocking {
            withTimeout(DELTA_REQUEST_TIMEOUT_MS) { handleDeltaRequest(req).getOrNull() }
        }
    }

    suspend fun handleIncomingBloom(
        msg: BloomFilterGossip,
        senderIp: String,
        @Suppress("UNUSED_PARAMETER") senderPort: Int
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
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
                // F5: requesterNodeId = nœud LOCAL (celui qui demande), pas l'émetteur distant
                val localNodeId = securityRepository.getIdentity().getOrNull()?.nodeId ?: "local"
                // F6: port serveur du pair résolu depuis peerRepository (pas le port éphémère client)
                val targetPort = peerRepository.peers.value
                    .firstOrNull { it.identity.nodeId == msg.senderNodeId }?.port
                if (targetPort == null) {
                    networkEventRepository.pushEvent("[GOSSIP] Port serveur inconnu pour ${msg.senderNodeId.take(8)} — DeltaSync ignoré")
                    return@withContext Result.success(Unit)
                }
                val req = DeltaSyncRequest(
                    requesterNodeId = localNodeId,
                    missingBlockIds = potentiallyMissing,
                    timestamp = System.currentTimeMillis()
                )
                gossipOutboundPort.sendDeltaSyncRequest(senderIp, targetPort, req)
                    .onSuccess { response -> handleDeltaResponse(response) }
                    .onFailure {
                        networkEventRepository.pushEvent("[GOSSIP] DeltaSync échoué vers $senderIp")
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
