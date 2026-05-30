package com.mobicloud.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val minBytes = HALF_GB
    val maxBytes = ((freeBytes * 0.80f).toLong()).coerceAtLeast(minBytes)
    val steps = (((maxBytes - minBytes) / HALF_GB).toInt() - 1).coerceAtLeast(0)

    var sliderValue by remember(settings.allocatedStorageBytes) {
        mutableFloatStateOf(settings.allocatedStorageBytes.coerceIn(minBytes, maxBytes).toFloat())
    }

    var storageVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storageVisible = true
    }

    val sectionEnter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AnimatedVisibility(visible = storageVisible, enter = sectionEnter) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F1F5)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Space I share",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1C1E)
                            )
                        }

                        Text(
                            text = sliderValue.toLong().toReadable(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A84FF)
                        )
                    }

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

                    Text(
                        text = "Used: ${usedBytes.toReadable()} · The more you share, the more files you can save with friends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
        }
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
