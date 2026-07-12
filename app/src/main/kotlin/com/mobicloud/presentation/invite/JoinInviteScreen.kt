package com.mobicloud.presentation.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.serialization.Serializable

/** cid/sp portés tels quels depuis le lien mobicloud://join décodé — cf. ClusterInvite. */
@Serializable
data class JoinInviteRoute(val cid: String, val sp: String = "")

@Composable
fun JoinInviteScreen(
    clusterId: String,
    hintedSpNodeId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JoinInviteViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val s = state) {
            is JoinInviteUiState.Idle -> {
                Text(
                    text = "Join this backup group?",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You'll share storage with this group, and get your own " +
                        "backup in return.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.join(clusterId, hintedSpNodeId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Join")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Not now")
                }
            }

            is JoinInviteUiState.Joining -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Joining…", style = MaterialTheme.typography.bodyMedium)
            }

            is JoinInviteUiState.Success -> {
                Text(
                    text = if (s.joinedIntendedCluster) {
                        "You're in! Your backup starts now."
                    } else {
                        "Your friend's group wasn't reachable, but you joined another " +
                            "active group instead."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }

            is JoinInviteUiState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.join(clusterId, hintedSpNodeId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
