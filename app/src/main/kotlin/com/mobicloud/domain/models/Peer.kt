package com.mobicloud.domain.models

enum class DiscoverySource {
    LOCAL_UDP,
    REMOTE_FIREBASE
}

data class Peer(
    val identity: NodeIdentity,
    val lastSeenTimestampMs: Long,
    val source: DiscoverySource = DiscoverySource.LOCAL_UDP,
    val ipAddress: String? = null,
    val port: Int? = null,
    val isActive: Boolean = true
)
