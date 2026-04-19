package com.mobicloud.domain.models.gossip

import kotlinx.serialization.Serializable

@Serializable
data class DeltaSyncResponse(
    val responderNodeId: String,
    val entries: List<DhtEntryDto>,
    val timestamp: Long
)
