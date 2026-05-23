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
import com.mobicloud.presentation.explorer.StoreState

@Composable
fun ErasureProgressIndicator(
    state: StoreState.InProgress,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                is StoreState.InProgress.Encoding -> {
                    Text(
                        text = "Preparing file…",
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

                is StoreState.InProgress.Encrypting -> {
                    Text(
                        text = "Securing file…",
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

                is StoreState.InProgress.Distributing -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Distributing…",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1C1C1E)
                        )
                        Text(
                            text = "${state.confirmed}/${state.total} sent",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8E8E93)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.confirmed.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0A84FF),
                        trackColor = Color(0xFFE5E5EA)
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
                                isFailed -> Color(0xFFFF3B30)
                                isConfirmed && isData -> Color(0xFF34C759)
                                isConfirmed && !isData -> Color(0xFF0A84FF)
                                isData -> Color(0xFFD0D0D5)
                                else -> Color(0xFFE5E5EA)
                            }
                            val borderColor = when {
                                isFailed -> Color(0xFFFF3B30)
                                isConfirmed -> Color.Transparent
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
