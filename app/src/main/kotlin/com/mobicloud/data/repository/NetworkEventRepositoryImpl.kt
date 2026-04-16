package com.mobicloud.data.repository

import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.repository.NetworkEventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_EVENTS = 50

@Singleton
class NetworkEventRepositoryImpl @Inject constructor() : NetworkEventRepository {

    private val _events = MutableStateFlow<List<NetworkLogEvent>>(emptyList())
    override val events: StateFlow<List<NetworkLogEvent>> = _events

    override fun pushEvent(message: String) {
        _events.update { current ->
            val newEvent = NetworkLogEvent(System.currentTimeMillis(), message)
            (listOf(newEvent) + current).take(MAX_EVENTS)
        }
    }
}
