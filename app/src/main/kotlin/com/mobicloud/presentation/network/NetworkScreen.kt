package com.mobicloud.presentation.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.presentation.network.components.ClusterTopologyCard
import com.mobicloud.presentation.network.components.RemoteClustersCard
import kotlinx.serialization.Serializable

@Serializable
object NetworkRoute

@Composable
fun NetworkScreen(
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel = hiltViewModel()
) {
    val topology by viewModel.clusterTopology.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
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
