package com.mobicloud.presentation.network.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import com.mobicloud.domain.models.ClusterNodeInfo
import com.mobicloud.domain.models.ClusterNodeStatus

/**
 * Story 13.1 — Liste des membres du cluster (Mode Simple).
 *
 * Avatars NEUTRES (sans initiale, conforme demande utilisateur du 2026-05-14) :
 *  - Coordinateur : cercle vert dégradé avec ★ centré
 *  - Membre standard : cercle vert sombre dégradé, vide
 *
 * Tri : Coordinateur (SP) en tête, puis membres triés par reliabilityScore décroissant.
 */
@Composable
fun MemberListCard(
    members: List<ClusterNodeInfo>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        members.forEach { node ->
            MemberRow(node = node)
        }
    }
}

@Composable
private fun MemberRow(node: ClusterNodeInfo) {
    val now = SystemClock.elapsedRealtime()  // F2 fix: même horloge que lastSeenMs (elapsedRealtime dans NetworkViewModel)
    val ageSec = ((now - node.lastSeenMs) / 1000L).coerceAtLeast(0L)
    val battery = node.batteryPercent?.let { "$it% battery" } ?: "unknown battery"
    val seenLabel = if (node.isLocal) "You" else "Connected ${ageSec}s ago"

    val displayName = when {
        node.isLocal && node.isSuperPair -> "You (Organizer)"
        node.isLocal -> "You"
        else -> "Member " + node.nodeId.take(4)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(isCoordinator = node.isSuperPair)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$seenLabel · $battery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusPill(node.nodeStatus)
    }
}

@Composable
private fun MemberAvatar(isCoordinator: Boolean) {
    val gradient = if (isCoordinator) {
        Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF006FD6)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFE5E5EA), Color(0xFFD0D0D5)))
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(gradient)
            .border(1.dp, Color(0xFFC7C7CC), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isCoordinator) {
            Text(
                text = "★",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusPill(status: ClusterNodeStatus) {
    val (label, color) = when (status) {
        ClusterNodeStatus.ACTIF -> "Active" to Color(0xFF34C759)
        ClusterNodeStatus.DEGRADED -> "Slow" to Color(0xFFFF9F0A)
        ClusterNodeStatus.OFFLINE -> "Offline" to Color(0xFFFF3B30)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
