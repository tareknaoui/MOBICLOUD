package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class ElectionMessageType {
    ELECTION,
    ALIVE,
    COORDINATOR
}
