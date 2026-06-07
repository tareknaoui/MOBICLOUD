package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Story 13.1 — Bannière santé contextuelle (Mode Simple uniquement).
 *
 * 4 états mutuellement exclusifs dérivés de (reliabilityScore, hasActivePeers, networkLabel) :
 *  Healthy · Slow · Searching · Degraded
 */
sealed class HealthState(val title: String, val subtitle: String, val color: Color) {
    data class Healthy(val peerCount: Int, val network: String) : HealthState(
        title    = "Your files are well protected",
        subtitle = "Connected to $peerCount member${if (peerCount > 1) "s" else ""} · $network",
        color    = Color(0xFF34C759)
    )
    data class Slow(val peerCount: Int, val network: String, val reliabilityPct: Int = 0) : HealthState(
        title    = "Reliability building up",
        subtitle = "Score $reliabilityPct% · $peerCount member${if (peerCount > 1) "s" else ""} · $network",
        color    = Color(0xFFFF9F0A)
    )
    data object Searching : HealthState(
        title    = "Looking for group members…",
        subtitle = "Open the Network tab to check your connection status",
        color    = Color(0xFF0A84FF)
    )
    data class Degraded(val peerCount: Int) : HealthState(
        title    = "Limited protection",
        subtitle = "Only $peerCount member${if (peerCount > 1) "s" else ""} online — check the Network tab",
        color    = Color(0xFFFF3B30)
    )
}

@Composable
fun HealthBanner(state: HealthState, modifier: Modifier = Modifier) {
    val icon = when (state) {
        is HealthState.Healthy   -> "✓"
        is HealthState.Slow      -> "~"
        is HealthState.Searching -> "…"
        is HealthState.Degraded  -> "!"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(state.color.copy(alpha = 0.08f))
            .border(1.dp, state.color.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(state.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = state.title,
                color = state.color,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.subtitle,
                color = state.color.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
