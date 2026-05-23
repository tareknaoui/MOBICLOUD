package com.mobicloud.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.presentation.explorer.DownloadState

@Composable
fun DownloadProgressIndicator(
    state: DownloadState,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (state !is DownloadState.Locating && state !is DownloadState.Downloading && state !is DownloadState.Decrypting) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state) {
                is DownloadState.Locating -> {
                    Text(
                        text = "Searching for file…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1C1C1E)
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0A84FF),
                        trackColor = Color(0xFFE5E5EA)
                    )
                }

                is DownloadState.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Downloading",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1C1C1E)
                        )
                        Text(
                            text = "${state.received}/${state.k} received",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8E8E93)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.received.toFloat() / state.k.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0A84FF),
                        trackColor = Color(0xFFE5E5EA)
                    )

                    val params = ErasureParameters()
                    val totalBlocks = params.k + params.n
                    val receivedByFragment = state.contributions.associateBy { it.fragmentIndex }
                    val failedFragments = state.failedFragmentIndices

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (index in 0 until totalBlocks) {
                            val isData = index < params.k
                            val contribution = receivedByFragment[index]
                            val blockColor = when {
                                index in failedFragments -> Color(0xFFFF3B30)
                                contribution != null && isData -> Color(0xFF34C759)
                                contribution != null && !isData -> Color(0xFF0A84FF)
                                isData -> Color(0xFFD0D0D5)
                                else -> Color(0xFFE5E5EA)
                            }
                            val borderColor = when {
                                index in failedFragments -> Color(0xFFFF3B30)
                                contribution != null -> Color.Transparent
                                else -> Color(0xFF9C8D86)
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(blockColor, RoundedCornerShape(3.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    if (state.contributions.isNotEmpty()) {
                        val uniqueContribs = state.contributions
                            .groupBy { it.nodeId }
                            .map { (_, contribs) -> contribs.first() }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (contrib in uniqueContribs) {
                                val label = buildString {
                                    append("${contrib.nodeId.take(6)}… ${contrib.latencyMs}ms")
                                    if (contrib.isFallback) append(" (fallback)")
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9C8D86)
                                )
                            }
                        }
                    }

                    if (state.slowNodeIds.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (nodeId in state.slowNodeIds) {
                                Text(
                                    text = "Waiting: ${nodeId.take(6)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }
                    }
                }

                is DownloadState.Decrypting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Decrypting",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1C1C1E)
                        )
                        Text(
                            text = "${state.processed}/${state.k} blocks",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8E8E93)
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0A84FF),
                        trackColor = Color(0xFFE5E5EA)
                    )
                }

            }

            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFF9C8D86),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
