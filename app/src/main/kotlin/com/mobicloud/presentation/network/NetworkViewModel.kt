package com.mobicloud.presentation.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.ClusterNodeInfo
import com.mobicloud.domain.models.ClusterNodeStatus
import com.mobicloud.domain.models.ClusterTopologyState
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.TransferChannelState
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.usecase.m11_join.MemberSnapshotCacheUseCase
import android.os.SystemClock
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

/**
 * Story 13.1 — Qualité de connexion humanisée pour l'écran Communauté (Mode Simple).
 */
enum class ConnectionQuality(val label: String) {
    EXCELLENT("Connexion excellente"),
    STABLE("Connexion stable"),
    LIMITED("Connexion limitée")
}

@HiltViewModel
class NetworkViewModel @Inject constructor(
    peerRepository: PeerRepository,
    private val securityRepository: SecurityRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase,
    private val nodeSettingsRepository: NodeSettingsRepository,
    @Named("transfer_channel_state") transferChannelStateFlow: @JvmSuppressWildcards StateFlow<TransferChannelState>
) : ViewModel() {

    private val localNodeIdFlow: StateFlow<String?> = flow {
        emit(securityRepository.getIdentity().getOrNull()?.nodeId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val clusterTopology: StateFlow<ClusterTopologyState> = combine(
        peerRepository.peers,
        localNodeIdFlow,
        diagnosticsRepository.diagnostics,
        memberSnapshotCacheUseCase.inMemory
    ) { peers, localNodeId, diagnostics, clusterMembers ->
        val now = SystemClock.elapsedRealtime()

        val localSuperPeer = peers.firstOrNull { peer ->
            peer.isSuperPair && (
                peer.source == DiscoverySource.LAN_MULTICAST ||
                peer.identity.nodeId == localNodeId
            )
        } ?: peers.singleOrNull { it.isSuperPair }

        val freshPeers = peers.filter { peer -> (now - peer.lastSeenTimestampMs) <= 120_000L }
        val freshPeersByNodeId = freshPeers.associateBy { it.identity.nodeId }

        // [P17 2026-05-19] Source de vérité = annuaire super-peer (membership logique),
        // pas la découverte LAN (qui échoue derrière AP isolation hotspot, NAT symétrique, etc.).
        // Pour chaque membre de l'annuaire, on enrichit depuis `peers` si dispo (chemin direct
        // connu) ; sinon on synthétise une entrée joignable via relais — cohérent avec le fallback
        // de `BlockDownloaderWithRelay` qui bascule relay-pull quand le pair n'est pas dans `peers`.
        var localNodes: List<ClusterNodeInfo> = clusterMembers.map { member ->
            val memberNodeIdHex = member.nodeId.toHexString()
            val isSelf = memberNodeIdHex == localNodeId
            val matchingPeer = freshPeersByNodeId[memberNodeIdHex]
            when {
                isSelf -> ClusterNodeInfo(
                    nodeId           = memberNodeIdHex,
                    isSuperPair      = member.role == MemberRole.SUPER_PAIR,
                    isLocal          = true,
                    batteryPercent   = diagnostics.batteryPercent,
                    reliabilityScore = diagnostics.reliabilityScore,
                    nodeStatus       = ClusterNodeStatus.ACTIF,
                    channel          = "Local",
                    lastSeenMs       = now
                )
                matchingPeer != null -> matchingPeer.toNodeInfo(localNodeId, diagnostics.batteryPercent, now)
                else -> ClusterNodeInfo(
                    nodeId           = memberNodeIdHex,
                    isSuperPair      = member.role == MemberRole.SUPER_PAIR,
                    isLocal          = false,
                    batteryPercent   = null,
                    reliabilityScore = 0f,
                    nodeStatus       = ClusterNodeStatus.ACTIF,
                    channel          = "Relais HA",
                    lastSeenMs       = now
                )
            }
        }.sortedWith(
            compareByDescending<ClusterNodeInfo> { it.isSuperPair }
                .thenByDescending { it.reliabilityScore }
        )

        // remotePeers = super-pairs vus hors cluster local (autres clusters)
        val remotePeers = freshPeers.filter { peer ->
            peer.isSuperPair && peer != localSuperPeer
        }

        if (localNodeId != null && localNodes.none { it.nodeId == localNodeId }) {
            val selfNode = ClusterNodeInfo(
                nodeId           = localNodeId,
                isSuperPair      = localSuperPeer?.identity?.nodeId == localNodeId,
                isLocal          = true,
                batteryPercent   = diagnostics.batteryPercent,
                reliabilityScore = diagnostics.reliabilityScore,
                nodeStatus       = ClusterNodeStatus.ACTIF,
                channel          = "Local",
                lastSeenMs       = now
            )
            localNodes = localNodes + selfNode
        }

        val remoteNodes = remotePeers
            .map { peer -> peer.toNodeInfo(localNodeId, null, now) }
            .sortedByDescending { it.reliabilityScore }

        ClusterTopologyState(nodes = localNodes, remoteSuperPeers = remoteNodes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ClusterTopologyState())

    // === Story 13.1 — Mode Diagnostics Avancés ===
    val isExpertMode: StateFlow<Boolean> = nodeSettingsRepository.observeExpertMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val connectionQuality: StateFlow<ConnectionQuality> = combine(
        diagnosticsRepository.diagnostics,
        transferChannelStateFlow
    ) { diag, channel ->
        val reliability = (diag.reliabilityScore * 100).toInt()
        when {
            reliability >= 70 && channel == TransferChannelState.DIRECT -> ConnectionQuality.EXCELLENT
            reliability >= 40 || channel == TransferChannelState.RELAY_HA -> ConnectionQuality.STABLE
            else -> ConnectionQuality.LIMITED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ConnectionQuality.STABLE)

    val coordinatorAlias: StateFlow<String?> = clusterTopology.map { topo ->
        topo.nodes.firstOrNull { it.isSuperPair && !it.isLocal }?.let { sp ->
            "Membre " + sp.nodeId.take(4)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun toggleExpertMode() = viewModelScope.launch {
        // F9 fix: lecture atomique depuis le repo (source de vérité DB) évite la race condition double-tap
        val current = nodeSettingsRepository.getSettings().isExpertModeEnabled
        nodeSettingsRepository.updateExpertMode(!current)
    }

    private fun Peer.toNodeInfo(
        localNodeId: String?,
        localBattery: Int?,
        now: Long
    ): ClusterNodeInfo {
        val isLocal = identity.nodeId == localNodeId
        val sinceMs = now - lastSeenTimestampMs
        val status = when {
            sinceMs > 45_000L -> ClusterNodeStatus.OFFLINE
            identity.reliabilityScore < 0.4f -> ClusterNodeStatus.DEGRADED
            else -> ClusterNodeStatus.ACTIF
        }
        val channel = when (source) {
            DiscoverySource.LAN_MULTICAST -> "WiFi local"
            DiscoverySource.RELAY_HA -> "Relais HA"
            else -> "Unknown"
        }
        return ClusterNodeInfo(
            nodeId = identity.nodeId,
            isSuperPair = isSuperPair,
            isLocal = isLocal,
            batteryPercent = if (isLocal) localBattery else null,
            reliabilityScore = identity.reliabilityScore,
            nodeStatus = status,
            channel = channel,
            lastSeenMs = lastSeenTimestampMs
        )
    }
}
