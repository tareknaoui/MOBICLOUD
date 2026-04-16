package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NetworkLogEvent
import kotlinx.coroutines.flow.StateFlow

interface NetworkEventRepository {
    val events: StateFlow<List<NetworkLogEvent>>
    fun pushEvent(message: String)
}
