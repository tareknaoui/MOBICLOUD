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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicloud.presentation.explorer.StoreState

@Composable
fun ErasureProgressIndicator(
    state: StoreState.InProgress,
    modifier: Modifier = Modifier
) {
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
                is StoreState.InProgress.Encoding -> {
                    Text(
                        text = "⚙ Encodage Erasure...",
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
                is StoreState.InProgress.Encrypting -> {
                    Text(
                        text = "🔒 Chiffrement AES-256...",
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
                is StoreState.InProgress.Distributing -> {
                    Text(
                        text = "⬆ Distribution (${state.confirmed}/${state.total} blocs)",
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (index in 0 until state.total) {
                            val isData = index < state.dataBlockCount
                            val isFailed = index in state.failedIndices
                            val isConfirmed = !isFailed && index in state.confirmedIndices

                            val blockColor = when {
                                isFailed -> Color(0xFFFF3333)
                                isConfirmed && isData -> Color(0xFF00FF41)
                                isConfirmed && !isData -> Color(0xFFFFB300)
                                isData -> Color(0xFF0D2B0D)   // pending data — dark green tint
                                else -> Color(0xFF2B2000)      // pending parity — dark amber tint
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(blockColor, RoundedCornerShape(2.dp))
                                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                                )
                                if (isFailed) {
                                    Text(
                                        text = "!$index",
                                        color = Color(0xFFFF3333),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "${state.confirmed} / ${state.total} blocs",
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
