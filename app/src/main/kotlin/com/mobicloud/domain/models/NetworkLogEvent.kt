package com.mobicloud.domain.models

data class NetworkLogEvent(
    val timestampMs: Long,
    val message: String
)
