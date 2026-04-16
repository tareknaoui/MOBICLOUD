package com.mobicloud.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NetworkServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    networkServiceController: NetworkServiceController,
    diagnosticsRepository: DiagnosticsRepository,
    networkEventRepository: NetworkEventRepository
) : ViewModel() {

    val serviceStatus: StateFlow<ServiceStatus> = networkServiceController.serviceStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ServiceStatus.STOPPED)

    val diagnostics: StateFlow<NodeDiagnostics> = diagnosticsRepository.diagnostics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NodeDiagnostics.DEFAULT)

    val networkEvents: StateFlow<List<NetworkLogEvent>> = networkEventRepository.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val hasActivePeers: StateFlow<Boolean> = diagnosticsRepository.diagnostics
        .map { it.activePeerCount > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
}
