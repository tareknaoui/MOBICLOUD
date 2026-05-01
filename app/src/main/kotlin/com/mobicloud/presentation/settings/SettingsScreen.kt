package com.mobicloud.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    // P5: coerceAtLeast(0) applied after - 1 to prevent steps = -1 on first composition (freeBytes=0)
    val steps = (((maxBytes - minBytes) / HALF_GB).toInt() - 1).coerceAtLeast(0)

    var sliderValue by remember(settings.allocatedStorageBytes) {
        mutableFloatStateOf(settings.allocatedStorageBytes.coerceIn(minBytes, maxBytes).toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Contribution au réseau",
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
            text = "${usedBytes.toReadable()} utilisés sur ${sliderValue.toLong().toGb()} GB alloués",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWarningDialog() },
            title = { Text("Réduire le quota ?") },
            text = { Text("Réduire ce quota supprimera des blocs hébergés du réseau") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmReduceQuota() }) {
                    Text("Confirmer")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWarningDialog() }) {
                    Text("Annuler")
                }
            }
        )
    }
}

private fun Long.toGb(): String = "%.1f".format(this / (1024.0 * 1024 * 1024))

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
