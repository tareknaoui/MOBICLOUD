package com.mobicloud.presentation.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.presentation.network.components.ClusterTopologyCard
import com.mobicloud.presentation.network.components.CommunitySummaryCard
import com.mobicloud.presentation.network.components.MemberListCard
import com.mobicloud.presentation.network.components.RemoteClustersCard
import kotlinx.serialization.Serializable

@Serializable
object NetworkRoute  // NE PAS RENOMMER — label utilisateur "Communauté" géré via strings.xml (Story 13.1)

@Composable
fun NetworkScreen(
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel = hiltViewModel()
) {
    val topology by viewModel.clusterTopology.collectAsStateWithLifecycle()
    val isExpertMode by viewModel.isExpertMode.collectAsStateWithLifecycle()
    val quality by viewModel.connectionQuality.collectAsStateWithLifecycle()
    val coordinatorAlias by viewModel.coordinatorAlias.collectAsStateWithLifecycle()

    val isCoordinator = topology.nodes.any { it.isLocal && it.isSuperPair }
    val memberCount = topology.nodes.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // === Story 13.1 — Mode Simple : résumé humain + liste membres (AC7) ===
        if (!isExpertMode) {
            CommunitySummaryCard(
                qualityLabel = quality.label,
                memberCount = memberCount,
                isCoordinator = isCoordinator,
                coordinatorAlias = coordinatorAlias,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "MEMBRES DU GROUPE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            MemberListCard(members = topology.nodes)
            Spacer(Modifier.height(12.dp))
        }

        // === Mode Expert : topologie technique + clusters distants (AC8) ===
        if (isExpertMode) {
            Spacer(Modifier.height(12.dp))
            ClusterTopologyCard(
                state = topology,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            RemoteClustersCard(
                remoteSuperPeers = topology.remoteSuperPeers,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
