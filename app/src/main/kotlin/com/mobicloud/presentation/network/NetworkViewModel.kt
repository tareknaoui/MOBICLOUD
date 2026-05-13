package com.mobicloud.presentation.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.ClusterNodeInfo
import com.mobicloud.domain.models.ClusterNodeStatus
import com.mobicloud.domain.models.ClusterTopologyState
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.PeerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(
    peerRepository: PeerRepository,
    private val identityRepository: IdentityRepository,
    diagnosticsRepository: DiagnosticsRepository
) : ViewModel() {

    private val localNodeIdFlow: StateFlow<String?> = flow {
        emit(identityRepository.getIdentity().getOrNull()?.nodeId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val clusterTopology: StateFlow<ClusterTopologyState> = combine(
        peerRepository.peers,
        localNodeIdFlow,
        diagnosticsRepository.diagnostics
    ) { peers, localNodeId, diagnostics ->
        val now = System.currentTimeMillis()

        // Un super-pair est "local" s'il est sur le LAN, s'il est ce device,
        // ou s'il est le seul SP visible (découvert via RELAY_HA après victoire Bully).
        val localSuperPeer = peers.firstOrNull { peer ->
            peer.isSuperPair && (
                peer.source == DiscoverySource.LAN_MULTICAST ||
                peer.identity.nodeId == localNodeId
            )
        } ?: peers.singleOrNull { it.isSuperPair }

        // Cluster local = membres non-super-pair + le super-pair local
        val localPeers = peers.filter { peer ->
            !peer.isSuperPair || peer == localSuperPeer
        }

        // Clusters distants = super-pairs qui ne sont PAS le super-pair local
        val remotePeers = peers.filter { peer ->
            peer.isSuperPair && peer != localSuperPeer
        }

        var localNodes = localPeers
            .map { peer -> peer.toNodeInfo(localNodeId, diagnostics.batteryPercent, now) }
            .sortedWith(
                compareByDescending<ClusterNodeInfo> { it.isSuperPair }
                    .thenByDescending { it.reliabilityScore }
            )

        // Le nœud local est filtré de peerRepository (processPeerList ignore l'auto-référence).
        // On l'injecte ici pour que le compteur X/50 soit cohérent sur tous les devices.
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

    private fun Peer.toNodeInfo(
        localNodeId: String?,
        localBattery: Int?,
        now: Long
    ): ClusterNodeInfo {
        val isLocal = identity.nodeId == localNodeId
        val sinceMs = now - lastSeenTimestampMs
        val status = when {
            sinceMs > 15_000L -> ClusterNodeStatus.OFFLINE
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
