package com.mobicloud.data.repository_impl

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

    override suspend fun registerOrUpdatePeer(identity: NodeIdentity, timestampMs: Long) {
        _activePeers.update { currentPeers ->
            val index = currentPeers.indexOfFirst { it.identity.publicId == identity.publicId }
            if (index == -1) {
                currentPeers + Peer(identity, timestampMs)
            } else {
                val updatedPeers = currentPeers.toMutableList()
                updatedPeers[index] = updatedPeers[index].copy(lastSeenTimestampMs = timestampMs)
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
