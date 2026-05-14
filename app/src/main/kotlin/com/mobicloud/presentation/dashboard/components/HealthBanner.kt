package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Story 13.1 — Bannière santé contextuelle (Mode Simple uniquement).
 *
 * Affiche un message d'état humain dérivé des signaux techniques pour rassurer l'utilisateur lambda.
 * 4 états mutuellement exclusifs, mappés depuis (reliabilityScore, hasActivePeers, networkLabel) :
 *  - Healthy   : reliability >= 70 && hasActivePeers (vert)
 *  - Slow      : reliability >= 40 (ambre)
 *  - Searching : !hasActivePeers (ambre)
 *  - Degraded  : reliability < 40 (rouge)
 */
sealed class HealthState(val message: String, val color: Color) {
    data class Healthy(val peerCount: Int, val network: String) :
        HealthState(
            message = "✓ Tout fonctionne · Connecté à $peerCount membres · $network",
            color = Color(0xFF00FF41)
        )

    data class Slow(val peerCount: Int, val network: String) :
        HealthState(
            message = "⚠ Connexion lente · $peerCount membres · $network",
            color = Color(0xFFFFB300)
        )

    data object Searching :
        HealthState(
            message = "🔍 À la recherche de membres à proximité…",
            color = Color(0xFFFFB300)
        )

    data class Degraded(val peerCount: Int) :
        HealthState(
            message = "⚠ Service dégradé · $peerCount membres",
            color = Color(0xFFFF3333)
        )
}

@Composable
fun HealthBanner(state: HealthState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(state.color.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, state.color.copy(alpha = 0.25f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = state.message,
            color = state.color,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
