package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.PeerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class FileNotFoundException(message: String) : Exception(message)

@Singleton
class LocalizeFileBlocksUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val dhtRepository: DhtRepository,
    private val peerRepository: PeerRepository
) {
    suspend fun invoke(fileHash: String): Result<Map<String, ResolvedBlockLocation>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entry = catalogRepository.getEntry(fileHash).getOrNull()
                    ?: return@runCatching run {
                        throw FileNotFoundException("Fichier introuvable dans le catalogue : $fileHash")
                    }

                val activePeers = peerRepository.peers.value
                    .filter { it.isActive && it.ipAddress != null && it.port != null }

                val resultMap = mutableMapOf<String, ResolvedBlockLocation>()

                for (fragmentLocation in entry.fragmentLocations) {
                    val blockId = fragmentLocation.fragmentHash

                    // PRIMARY : pairs actifs correspondant aux nodeIds de la FragmentLocation
                    val primaryPeer = activePeers
                        .filter { it.identity.nodeId in fragmentLocation.nodeIds }
                        .maxByOrNull { it.identity.reliabilityScore }

                    if (primaryPeer != null) {
                        resultMap[blockId] = ResolvedBlockLocation(
                            blockId = blockId,
                            fragmentIndex = fragmentLocation.fragmentIndex,
                            nodeId = primaryPeer.identity.nodeId,
                            ipAddress = primaryPeer.ipAddress!!,
                            port = primaryPeer.port!!,
                            reliabilityScore = primaryPeer.identity.reliabilityScore
                        )
                        continue
                    }

                    // DHT FALLBACK : lookup local Room
                    val dhtEntry = dhtRepository.findByBlockId(blockId).getOrNull()
                    if (dhtEntry != null) {
                        val dhtPeer = activePeers.find { it.identity.nodeId == dhtEntry.nodeId }
                        resultMap[blockId] = ResolvedBlockLocation(
                            blockId = blockId,
                            fragmentIndex = fragmentLocation.fragmentIndex,
                            nodeId = dhtEntry.nodeId,
                            ipAddress = dhtEntry.ipAddress,
                            port = dhtEntry.port,
                            reliabilityScore = dhtPeer?.identity?.reliabilityScore ?: 0f
                        )
                        continue
                    }

                    // RING RELAY : déléguer au nœud responsable dans l'anneau
                    if (activePeers.isNotEmpty()) {
                        val ring = ConsistentHashRing(activePeers.map { it.identity.nodeId })
                        val responsibleNodeId = ring.getPartition(blockId)
                        val relayPeer = activePeers.find { it.identity.nodeId == responsibleNodeId }
                        if (relayPeer != null) {
                            val relayEntry = dhtRepository.remoteLookup(
                                blockId,
                                relayPeer.ipAddress!!,
                                relayPeer.port!!
                            ).getOrNull()
                            if (relayEntry != null) {
                                resultMap[blockId] = ResolvedBlockLocation(
                                    blockId = blockId,
                                    fragmentIndex = fragmentLocation.fragmentIndex,
                                    nodeId = relayEntry.nodeId,
                                    ipAddress = relayEntry.ipAddress,
                                    port = relayEntry.port,
                                    reliabilityScore = 0f
                                )
                            }
                        }
                    }
                }

                resultMap
            }
        }
}
