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
                        text = "Encodage du fichier…",
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

                is StoreState.InProgress.Encrypting -> {
                    Text(
                        text = "Chiffrement AES-256…",
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

                is StoreState.InProgress.Distributing -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Distribution en cours",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B1816)
                        )
                        Text(
                            text = "${state.confirmed}/${state.total} blocs",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF6B625B)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.confirmed.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFD9633F),
                        trackColor = Color(0xFFFFE0D1)
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
                                isFailed -> Color(0xFFC62828)
                                isConfirmed && isData -> Color(0xFF4CAF50)
                                isConfirmed && !isData -> Color(0xFFD9633F)
                                isData -> Color(0xFFD9F0E6)
                                else -> Color(0xFFFFE0D1)
                            }
                            val borderColor = when {
                                isFailed -> Color(0xFFC62828)
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
                    text = "Annuler",
                    color = Color(0xFF9C8D86),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
