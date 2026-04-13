package com.mobicloud.domain.repository

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import kotlinx.coroutines.flow.StateFlow

interface PeerRegistry {
    val activePeers: StateFlow<List<Peer>>
    
    suspend fun registerOrUpdatePeer(
        identity: NodeIdentity, 
        timestampMs: Long,
        source: DiscoverySource = DiscoverySource.LOCAL_UDP,
        ipAddress: String? = null,
        port: Int? = null
    )
    suspend fun evictStalePeers(timeoutMs: Long, currentTimeMs: Long)
}
