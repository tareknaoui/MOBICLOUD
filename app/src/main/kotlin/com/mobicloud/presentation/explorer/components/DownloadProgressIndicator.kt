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
                        text = "Localisation des blocs…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1B1816)
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFD9633F),
                        trackColor = Color(0xFFFFE0D1)
                    )
                }

                is DownloadState.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Téléchargement",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B1816)
                        )
                        Text(
                            text = "${state.received}/${state.k} blocs",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF6B625B)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.received.toFloat() / state.k.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFD9633F),
                        trackColor = Color(0xFFFFE0D1)
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
                                index in failedFragments -> Color(0xFFC62828)
                                contribution != null && isData -> Color(0xFF4CAF50)
                                contribution != null && !isData -> Color(0xFFD9633F)
                                isData -> Color(0xFFD9F0E6)
                                else -> Color(0xFFFFE0D1)
                            }
                            val borderColor = when {
                                index in failedFragments -> Color(0xFFC62828)
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
                                    if (contrib.isFallback) append(" (secours)")
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
                                    text = "En attente : ${nodeId.take(6)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFBF360C)
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
                            text = "Déchiffrement",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B1816)
                        )
                        Text(
                            text = "${state.processed}/${state.k} blocs",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF6B625B)
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFD9633F),
                        trackColor = Color(0xFFFFE0D1)
                    )
                }

            }

            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Annuler",
                    color = Color(0xFF9C8D86),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
