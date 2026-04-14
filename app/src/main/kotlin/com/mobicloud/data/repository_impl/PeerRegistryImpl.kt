package com.mobicloud.data.repository_impl

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.PeerRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PeerRegistryImpl : PeerRegistry {
    
    private val _activePeers = MutableStateFlow<List<Peer>>(emptyList())
    override val activePeers: StateFlow<List<Peer>> = _activePeers.asStateFlow()

    override suspend fun registerOrUpdatePeer(
        identity: NodeIdentity, 
        timestampMs: Long,
        source: DiscoverySource,
        ipAddress: String?,
        port: Int?
    ) {
        _activePeers.update { currentPeers ->
            val index = currentPeers.indexOfFirst { it.identity.nodeId == identity.nodeId }
            if (index == -1) {
                currentPeers + Peer(identity, timestampMs, source, ipAddress, port)
            } else {
                val updatedPeers = currentPeers.toMutableList()
                val existing = updatedPeers[index]
                
                // On met à jour l'IP uniquement si elle est fournie, ou on écrase si source = Firebase
                val newSource = if (source == DiscoverySource.REMOTE_FIREBASE) source else existing.source
                val newIp = ipAddress ?: existing.ipAddress
                val newPort = port ?: existing.port

                updatedPeers[index] = existing.copy(
                    lastSeenTimestampMs = timestampMs,
                    source = newSource,
                    ipAddress = newIp,
                    port = newPort
                )
                updatedPeers
            }
        }
    }

    override suspend fun evictStalePeers(timeoutMs: Long, currentTimeMs: Long) {
        _activePeers.update { currentPeers ->
            currentPeers.filter { peer ->
                (currentTimeMs - peer.lastSeenTimestampMs) <= timeoutMs
            }
        }
    }
}
