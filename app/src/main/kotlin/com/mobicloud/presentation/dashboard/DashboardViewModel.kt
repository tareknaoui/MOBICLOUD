package com.mobicloud.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.models.NodeRole
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NetworkServiceController
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.CircuitBreakerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    networkServiceController: NetworkServiceController,
    diagnosticsRepository: DiagnosticsRepository,
    networkEventRepository: NetworkEventRepository,
    private val peerRepository: PeerRepository,
    private val identityRepository: IdentityRepository,
    circuitBreakerUseCase: CircuitBreakerUseCase
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

    // StateFlow Eagerly : résolu une fois au démarrage, jamais null après init
    private val localNodeIdFlow: StateFlow<String?> = flow {
        emit(identityRepository.getIdentity().getOrNull()?.nodeId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nodeRole: StateFlow<NodeRole> = combine(
        peerRepository.peers,
        localNodeIdFlow
    ) { peers, localNodeId ->
        if (localNodeId != null && peers.any { p -> p.isSuperPair && p.isActive && p.identity.nodeId == localNodeId }) {
            NodeRole.SUPER_PAIR
        } else {
            NodeRole.PEER
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NodeRole.PEER)

    // AC#6 — Badge "Réseau instable" : vrai si le Circuit-Breaker est actif (Story 3.4)
    val isNetworkUnstable: StateFlow<Boolean> = circuitBreakerUseCase.isCircuitOpen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
}
