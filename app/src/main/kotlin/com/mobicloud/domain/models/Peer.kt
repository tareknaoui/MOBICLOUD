package com.mobicloud.domain.models

enum class DiscoverySource {
    REMOTE_FIREBASE,
    LAN_MULTICAST,
    RELAY_HA
}

data class Peer(
    val identity: NodeIdentity,
    val lastSeenTimestampMs: Long,
    val source: DiscoverySource = DiscoverySource.REMOTE_FIREBASE,
    val ipAddress: String? = null,
    val port: Int? = null,
    val isActive: Boolean = true,
    val isSuperPair: Boolean = false,
    val freeStorageBytes: Long = 0L
)
