package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import kotlinx.coroutines.flow.StateFlow

interface PeerRegistry {
    val activePeers: StateFlow<List<Peer>>
    
    suspend fun registerOrUpdatePeer(identity: NodeIdentity, timestampMs: Long)
    suspend fun evictStalePeers(timeoutMs: Long, currentTimeMs: Long)
}
