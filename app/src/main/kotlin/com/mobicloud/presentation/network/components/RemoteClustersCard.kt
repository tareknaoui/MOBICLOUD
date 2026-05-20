package com.mobicloud.presentation.network.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
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

private val MauvePrimary = Color(0xFF0A84FF)
private val Amber = Color(0xFFFF9F0A)
private val StatusRed = Color(0xFFFF3B30)
private val MauveAccent = Color(0xFF0A84FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteClustersCard(
    remoteSuperPeers: List<ClusterNodeInfo>,
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
                text = "Autres groupes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = MauveAccent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${remoteSuperPeers.size} cluster${if (remoteSuperPeers.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MauveAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            thickness = 1.dp
        )

        if (remoteSuperPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun autre groupe détecté",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            remoteSuperPeers.forEachIndexed { index, node ->
                RemoteClusterListItem(node = node, onClick = { selectedNode = node })
                if (index < remoteSuperPeers.lastIndex) {
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
            RemoteClusterDetailSheet(node = selectedNode!!)
        }
    }
}

@Composable
private fun RemoteClusterListItem(node: ClusterNodeInfo, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(
                color = MauveAccent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Hub,
                    contentDescription = null,
                    tint = MauveAccent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        },
        headlineContent = {
            Text(
                text = truncateNodeId(node.nodeId),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                text = "Organisateur · ${node.channel}",
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
                Spacer(Modifier.height(4.dp))
                RemoteStatusBadge(node.nodeStatus)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun RemoteStatusBadge(status: ClusterNodeStatus) {
    val (label, color) = when (status) {
        ClusterNodeStatus.ACTIF -> "Actif" to MauvePrimary
        ClusterNodeStatus.DEGRADED -> "Dégradé" to Amber
        ClusterNodeStatus.OFFLINE -> "Hors-ligne" to StatusRed
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
private fun RemoteClusterDetailSheet(node: ClusterNodeInfo) {
    val clipboardManager = LocalClipboardManager.current
    val now = System.currentTimeMillis()
    val sinceMs = now - node.lastSeenMs
    val lastSeenLabel = if (sinceMs < 60_000L) "il y a ${sinceMs / 1000} s"
    else "il y a ${sinceMs / 60_000} min"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Autre groupe",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        RemoteDetailRow(
            label = "ID Super-Pair",
            value = node.nodeId,
            valueModifier = Modifier.clickable {
                clipboardManager.setText(AnnotatedString(node.nodeId))
            }
        )
        RemoteDetailRow(label = "Canal", value = node.channel)
        RemoteDetailRow(label = "Fiabilité", value = "${(node.reliabilityScore * 100).toInt()}%")
        RemoteDetailRow(label = "Dernier heartbeat", value = lastSeenLabel)
        RemoteDetailRow(label = "Membres", value = "Accès limité au super-pair")
    }
}

@Composable
private fun RemoteDetailRow(
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
