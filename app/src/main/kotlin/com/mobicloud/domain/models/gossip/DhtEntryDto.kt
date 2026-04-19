package com.mobicloud.domain.models.gossip

import kotlinx.serialization.Serializable

@Serializable
data class DhtEntryDto(
    val blockId: String,
    val nodeId: String,
    val ipAddress: String,
    val port: Int,
    val timestamp: Long
)
