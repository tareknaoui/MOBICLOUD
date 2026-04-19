package com.mobicloud.domain.models.gossip

import kotlinx.serialization.Serializable

@Serializable
data class DeltaSyncRequest(
    val requesterNodeId: String,
    val missingBlockIds: List<String>,
    val timestamp: Long
)
