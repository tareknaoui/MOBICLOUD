package com.mobicloud.domain.models

enum class ClusterNodeStatus { ACTIF, DEGRADED, OFFLINE }

data class ClusterNodeInfo(
    val nodeId: String,
    val isSuperPair: Boolean,
    val isLocal: Boolean,
    val batteryPercent: Int?,
    val reliabilityScore: Float,
    val nodeStatus: ClusterNodeStatus,
    val channel: String,
    val lastSeenMs: Long,
    val displayName: String? = null
)

data class ClusterTopologyState(
    val nodes: List<ClusterNodeInfo> = emptyList(),
    val remoteSuperPeers: List<ClusterNodeInfo> = emptyList()
)
