package com.mobicloud.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.models.NodeRole
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.models.TransferChannelState
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NetworkServiceController
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.CircuitBreakerUseCase
import com.mobicloud.domain.usecase.m11_join.MemberSnapshotCacheUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DashboardViewModel @Inject constructor(
    networkServiceController: NetworkServiceController,
    diagnosticsRepository: DiagnosticsRepository,
    networkEventRepository: NetworkEventRepository,
    private val peerRepository: PeerRepository,
    private val identityRepository: IdentityRepository,
    circuitBreakerUseCase: CircuitBreakerUseCase,
    private val nodeSettingsRepository: NodeSettingsRepository,
    hostedBlockRepository: HostedBlockRepository,
    memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase,
    @Named("transfer_channel_state") transferChannelStateFlow: @JvmSuppressWildcards StateFlow<TransferChannelState>
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

    private val localNodeIdFlow: StateFlow<String?> = flow {
        emit(identityRepository.getIdentity().getOrNull()?.nodeId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nodeRole: StateFlow<NodeRole> = combine(
        peerRepository.peers,
        localNodeIdFlow
    ) { peers, localNodeId ->
        val match = localNodeId != null && peers.any { p -> p.isSuperPair && p.isActive && p.identity.nodeId == localNodeId }
        android.util.Log.i("DashboardVM", "[ROLE-DIAG] localNodeId=${localNodeId?.take(8)} peersCount=${peers.size} → ${if (match) "SUPER_PAIR" else "PEER"}")
        if (match) NodeRole.SUPER_PAIR else NodeRole.PEER
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NodeRole.PEER)

    val isNetworkUnstable: StateFlow<Boolean> = circuitBreakerUseCase.isCircuitOpen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val relayState: StateFlow<TransferChannelState> = transferChannelStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TransferChannelState.OFFLINE)

    // === Story 13.1 — toggle Mode Diagnostics Avancés ===
    val isExpertMode: StateFlow<Boolean> = nodeSettingsRepository.observeExpertMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    // KPI "Communauté" — total membres du cluster (self inclus, cohérent avec l'onglet Communauté)
    val communitySize: StateFlow<Int> = memberSnapshotCacheUseCase.inMemory
        .map { members -> members.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    // KPI sémantique "Ma contribution" — quota partagé persisté en NodeSettings
    val allocatedStorageBytes: StateFlow<Long> = nodeSettingsRepository.observeSettings()
        .map { it.allocatedStorageBytes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    // KPI sémantique "Fichiers protégés" — count des fragments hébergés
    val hostedBlockCount: StateFlow<Int> = hostedBlockRepository.observeHostedBlockCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    // Octets réellement hébergés (vs quota alloué)
    val hostedStorageBytes: StateFlow<Long> = hostedBlockRepository.observeTotalHostedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    fun toggleExpertMode() = viewModelScope.launch {
        // F9 fix: lecture atomique depuis le repo (source de vérité DB) évite la race condition double-tap
        val current = nodeSettingsRepository.getSettings().isExpertModeEnabled
        nodeSettingsRepository.updateExpertMode(!current)
    }
}
