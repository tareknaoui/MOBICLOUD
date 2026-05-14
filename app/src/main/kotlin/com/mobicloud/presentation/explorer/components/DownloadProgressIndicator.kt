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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.presentation.explorer.DownloadState

@Composable
fun DownloadProgressIndicator(
    state: DownloadState,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // [Review][Patch] P3 — AC4 : le bouton Annuler doit aussi apparaître pendant Locating.
    // Avant ce patch, le return excluait Locating → AC4 violé.
    if (state !is DownloadState.Locating && state !is DownloadState.Downloading && state !is DownloadState.Decrypting) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0xFF333333), shape = RoundedCornerShape(4.dp)),
        color = Color(0xFF000000),
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                is DownloadState.Locating -> {
                    Text(
                        text = "⟳ Localisation des blocs...",
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00FF41),
                        trackColor = Color(0xFF1A1A1A)
                    )
                }

                is DownloadState.Downloading -> {
                    Text(
                        text = "⬇ ${state.received}/${state.k} blocs",
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    LinearProgressIndicator(
                        progress = { state.received.toFloat() / state.k.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00FF41),
                        trackColor = Color(0xFF1A1A1A)
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
                                index in failedFragments -> Color(0xFFFF3333)
                                contribution != null && isData -> Color(0xFF00FF41)
                                contribution != null && !isData -> Color(0xFFFFB300)
                                isData -> Color(0xFF0D2B0D)
                                else -> Color(0xFF2B2000)
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(blockColor, RoundedCornerShape(2.dp))
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
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
                                    append("${contrib.nodeId.take(4)}... ${contrib.latencyMs}ms")
                                    if (contrib.isFallback) append(" (secours)")
                                }
                                Text(
                                    text = label,
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (state.slowNodeIds.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (nodeId in state.slowNodeIds) {
                                Text(
                                    text = "⏳ ${nodeId.take(4)}... Attente",
                                    color = Color(0xFFFFB300),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                is DownloadState.Decrypting -> {
                    Text(
                        text = "🔓 Déchiffrement ${state.processed}/${state.k} blocs",
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00FF41),
                        trackColor = Color(0xFF1A1A1A)
                    )
                }

            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "✕ Annuler",
                    color = Color(0xFFFF3333),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
