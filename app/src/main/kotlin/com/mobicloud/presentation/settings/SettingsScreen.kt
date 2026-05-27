package com.mobicloud.presentation.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
object SettingsRoute

private const val HALF_GB = 512L * 1024 * 1024

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val usedBytes by viewModel.usedStorageBytes.collectAsStateWithLifecycle()
    val freeBytes by viewModel.freeSpaceBytes.collectAsStateWithLifecycle()
    val showWarning by viewModel.showWarningDialog.collectAsStateWithLifecycle()
    val isExpertMode by viewModel.isExpertMode.collectAsStateWithLifecycle()
    val showLeaveDialog by viewModel.showLeaveDialog.collectAsStateWithLifecycle()
    val isLeaving by viewModel.isLeaving.collectAsStateWithLifecycle()

    val minBytes = HALF_GB
    val maxBytes = ((freeBytes * 0.80f).toLong()).coerceAtLeast(minBytes)
    val steps = (((maxBytes - minBytes) / HALF_GB).toInt() - 1).coerceAtLeast(0)

    var sliderValue by remember(settings.allocatedStorageBytes) {
        mutableFloatStateOf(settings.allocatedStorageBytes.coerceIn(minBytes, maxBytes).toFloat())
    }

    var storageVisible by remember { mutableStateOf(false) }
    var displayVisible by remember { mutableStateOf(false) }
    var networkVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storageVisible = true
        delay(100)
        displayVisible = true
        delay(100)
        networkVisible = true
    }

    val sectionEnter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AnimatedVisibility(visible = storageVisible, enter = sectionEnter) {
            Column {
                Text(
                    text = "Space I share",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        viewModel.requestUpdateAllocatedStorage(sliderValue.roundToLong())
                    },
                    valueRange = minBytes.toFloat()..maxBytes.toFloat(),
                    steps = steps.coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Used: ${usedBytes.toReadable()} · The more you share, the more you can save with your friends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        AnimatedVisibility(visible = displayVisible, enter = sectionEnter) {
            Column {
                Text(
                    text = "DISPLAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Advanced Diagnostics Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Shows technical details (NodeId, network logs, cluster topology).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = isExpertMode,
                        onCheckedChange = viewModel::updateExpertMode
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        AnimatedVisibility(visible = networkVisible, enter = sectionEnter) {
            Column {
                Text(
                    text = "NETWORK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = viewModel::requestLeaveCluster,
                    enabled = !isLeaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(targetState = isLeaving, label = "leaveBtn") { leaving ->
                        if (leaving) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onError,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Migrating...")
                            }
                        } else {
                            Text("Leave the cluster")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your hosted blocks will be migrated to other nodes before disconnecting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLeaveDialog,
            title = { Text("Leave the cluster?") },
            text = { Text("Your hosted blocks will be migrated to other nodes. This operation takes a few seconds.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLeaveCluster) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLeaveDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWarningDialog() },
            title = { Text("Reduce shared space?") },
            text = { Text("If you reduce this space, some of your friends' files will no longer be protected on your device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmReduceQuota() }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWarningDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun Long.toReadable(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.2f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$this B"
    }
}
