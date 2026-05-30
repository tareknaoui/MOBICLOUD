package com.mobicloud.presentation.explorer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
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
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                label = "downloadState"
            ) { animatedState ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (animatedState) {
                        is DownloadState.Locating -> {
                            val label = if (animatedState.isPreview) "Preview: Searching for file…" else "Searching for file…"
                            Text(
                                text = label,
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
                                val label = if (animatedState.isPreview) "Previewing" else "Downloading"
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1C1C1E)
                                )
                                Text(
                                    text = "${animatedState.received}/${animatedState.k} received",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { animatedState.received.toFloat() / animatedState.k.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF0A84FF),
                                trackColor = Color(0xFFE5E5EA)
                            )

                            val params = ErasureParameters()
                            val totalBlocks = params.k + params.n
                            val receivedByFragment = animatedState.contributions.associateBy { it.fragmentIndex }
                            val failedFragments = animatedState.failedFragmentIndices

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (index in 0 until totalBlocks) {
                                    val isData = index < params.k
                                    val contribution = receivedByFragment[index]
                                    val targetColor = when {
                                        index in failedFragments -> Color(0xFFFF3B30)
                                        contribution != null && isData -> Color(0xFF34C759)
                                        contribution != null && !isData -> Color(0xFF0A84FF)
                                        isData -> Color(0xFFD0D0D5)
                                        else -> Color(0xFFE5E5EA)
                                    }
                                    val targetBorder = when {
                                        index in failedFragments -> Color(0xFFFF3B30)
                                        contribution != null -> Color.Transparent
                                        else -> Color(0xFF9C8D86)
                                    }
                                    BlockCell(color = targetColor, borderColor = targetBorder)
                                }
                            }

                            if (animatedState.contributions.isNotEmpty()) {
                                val uniqueContribs = animatedState.contributions
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

                            if (animatedState.slowNodeIds.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (nodeId in animatedState.slowNodeIds) {
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
                                val label = if (animatedState.isPreview) "Preview: Decrypting" else "Decrypting"
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1C1C1E)
                                )
                                Text(
                                    text = "${animatedState.processed}/${animatedState.k} blocks",
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

                        else -> Unit
                    }
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

@Composable
private fun BlockCell(color: Color, borderColor: Color) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "blockColor"
    )
    val animatedBorder by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(300),
        label = "blockBorder"
    )
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(animatedColor, RoundedCornerShape(3.dp))
            .border(1.dp, animatedBorder, RoundedCornerShape(3.dp))
    )
}
