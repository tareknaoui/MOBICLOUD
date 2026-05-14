package com.mobicloud.presentation.network.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * Story 13.1 — Résumé humain de la communauté (Mode Simple uniquement).
 *
 * @param qualityLabel `"Connexion excellente"` / `"Connexion stable"` / `"Connexion limitée"`
 * @param memberCount Nombre de membres connectés au cluster local (inclus soi-même)
 * @param isCoordinator Si le nœud local est Coordinateur (Super-Pair)
 * @param coordinatorAlias Alias court du Coordinateur si nœud n'est pas SP (ex: `"Membre 4f2a"`)
 */
@Composable
fun CommunitySummaryCard(
    qualityLabel: String,
    memberCount: Int,
    isCoordinator: Boolean,
    coordinatorAlias: String?,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF00FF41)
    val subtitle = buildString {
        append("$memberCount membres à proximité · ")
        append(
            if (isCoordinator) "Vous coordonnez le groupe"
            else "Coordinateur : ${coordinatorAlias ?: "—"}"
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.25f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = qualityLabel,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
