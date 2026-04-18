package com.mobicloud.domain.models

data class DhtEntry(
    val blockId: String,
    val nodeId: String,
    val ipAddress: String,
    val port: Int,
    val timestamp: Long
)
