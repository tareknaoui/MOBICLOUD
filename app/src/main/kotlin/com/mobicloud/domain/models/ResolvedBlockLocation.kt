package com.mobicloud.domain.models

data class ResolvedBlockLocation(
    val blockId: String,
    val fragmentIndex: Int,
    val nodeId: String,
    val ipAddress: String,
    val port: Int,
    val reliabilityScore: Float
)
