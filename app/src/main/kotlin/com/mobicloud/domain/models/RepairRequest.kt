package com.mobicloud.domain.models

data class RepairRequest(
    val blockId: String,
    val destinationIp: String,
    val port: Int
)
