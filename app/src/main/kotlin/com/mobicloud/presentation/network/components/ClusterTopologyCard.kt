package com.mobicloud.presentation.network.components

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicloud.domain.models.ClusterNodeInfo
import com.mobicloud.domain.models.ClusterNodeStatus
import com.mobicloud.domain.models.ClusterTopologyState
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE

private val MauvePrimary = Color(0xFF0A84FF)
private val Amber = Color(0xFFFF9F0A)
private val StatusRed = Color(0xFFFF3B30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterTopologyCard(
    state: ClusterTopologyState,
    modifier: Modifier = Modifier
) {
    var selectedNode by remember { mutableStateOf<ClusterNodeInfo?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        // En-tête
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your group",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Story 12.1 — indicateur de charge cluster (admission par memberCount)
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${state.nodes.size} / $MAX_CLUSTER_SIZE members",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            thickness = 1.dp
        )

        if (state.nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No members found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            state.nodes.forEachIndexed { index, node ->
                NodeListItem(node = node, onClick = { selectedNode = node })
                if (index < state.nodes.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        thickness = 1.dp
                    )
                }
            }
        }
    }

    if (selectedNode != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedNode = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            NodeDetailSheet(node = selectedNode!!)
        }
    }
}

@Composable
private fun NodeListItem(node: ClusterNodeInfo, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(
                color = if (node.isSuperPair)
                    MauvePrimary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (node.isSuperPair) Icons.Filled.Star else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (node.isSuperPair) MauvePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = truncateNodeId(node.nodeId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (node.isLocal) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = Amber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "MOI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = if (node.isSuperPair) "Organizer · ${node.channel}" else node.channel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${(node.reliabilityScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!node.isSuperPair) {
                    Spacer(Modifier.height(4.dp))
                    StatusBadge(node.nodeStatus)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun StatusBadge(status: ClusterNodeStatus) {
    val (label, color) = when (status) {
        ClusterNodeStatus.ACTIF -> "Active" to MauvePrimary
        ClusterNodeStatus.DEGRADED -> "Degraded" to Amber
        ClusterNodeStatus.OFFLINE -> "Offline" to StatusRed
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NodeDetailSheet(node: ClusterNodeInfo) {
    val clipboardManager = LocalClipboardManager.current
    val now = SystemClock.elapsedRealtime()
    val sinceMs = now - node.lastSeenMs
    val lastSeenLabel = if (sinceMs < 60_000L) "${sinceMs / 1000}s ago"
    else "${sinceMs / 60_000}min ago"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Node details",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        DetailRow(
            label = "ID",
            value = node.nodeId,
            valueModifier = Modifier.clickable {
                clipboardManager.setText(AnnotatedString(node.nodeId))
            }
        )
        DetailRow(label = "Role", value = if (node.isSuperPair) "Group organizer" else "Member")
        DetailRow(label = "Reliability", value = "${(node.reliabilityScore * 100).toInt()}%")
        if (node.batteryPercent != null) {
            DetailRow(label = "Battery", value = "${node.batteryPercent}%")
        }
        DetailRow(label = "Channel", value = node.channel)
        DetailRow(label = "Last heartbeat", value = lastSeenLabel)
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = valueModifier
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
}

private fun truncateNodeId(nodeId: String): String =
    if (nodeId.length <= 12) nodeId
    else "${nodeId.take(6)}…${nodeId.takeLast(3)}"
