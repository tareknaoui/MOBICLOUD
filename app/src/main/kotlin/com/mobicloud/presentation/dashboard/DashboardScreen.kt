package com.mobicloud.presentation.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.domain.models.NetworkType
import com.mobicloud.domain.models.NodeRole
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.presentation.dashboard.components.KpiDiagnosticCard
import com.mobicloud.presentation.dashboard.components.RadarLogConsole
import com.mobicloud.presentation.dashboard.components.ReliabilityGauge
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
object DashboardRoute  // NE PAS MODIFIER — câblé dans la navigation Story 1.2

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val diagnostics     by viewModel.diagnostics.collectAsStateWithLifecycle()
    val networkEvents   by viewModel.networkEvents.collectAsStateWithLifecycle()
    val nodeRole        by viewModel.nodeRole.collectAsStateWithLifecycle()
    val isNetworkUnstable by viewModel.isNetworkUnstable.collectAsStateWithLifecycle()

    val isExpertMode    by viewModel.isExpertMode.collectAsStateWithLifecycle()
    val communitySize   by viewModel.communitySize.collectAsStateWithLifecycle()
    val allocatedBytes  by viewModel.allocatedStorageBytes.collectAsStateWithLifecycle()
    val hostedBlockCount by viewModel.hostedBlockCount.collectAsStateWithLifecycle()
    val hostedBytes      by viewModel.hostedStorageBytes.collectAsStateWithLifecycle()

    val uptimeFormatted = formatUptime(diagnostics.uptimeMs)
    val networkLabel = when (diagnostics.networkType) {
        NetworkType.WIFI     -> "Wifi"
        NetworkType.CELLULAR -> "4G"
        NetworkType.UNKNOWN  -> "—"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hero gradient card ──
        DashboardHero(
            score        = diagnostics.reliabilityScore,
            nodeRole     = nodeRole,
            isExpertMode = isExpertMode,
            isUnstable   = isNetworkUnstable
        )

        Spacer(Modifier.height(20.dp))

        // ── Section Aperçu ──
        SectionLabel("Overview")
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiDiagnosticCard(
                label       = "Network",
                value       = networkLabel,
                hint        = "Active connection type",
                icon        = Icons.Filled.Wifi,
                accentColor = Color(0xFF0A84FF),
                modifier    = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label       = "My group",
                value       = "$communitySize/$MAX_CLUSTER_SIZE",
                hint        = "Connected members",
                icon        = Icons.Filled.Group,
                accentColor = Color(0xFF34C759),
                modifier    = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        StorageCard(
            usedBytes      = hostedBytes,
            allocatedBytes = allocatedBytes,
            blockCount     = hostedBlockCount,
            modifier       = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // ── Mode Expert ──
        if (isExpertMode) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Advanced info")
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiDiagnosticCard(
                    label    = "Session duration",
                    value    = uptimeFormatted,
                    modifier = Modifier.weight(1f)
                )
                KpiDiagnosticCard(
                    label    = "Network used",
                    value    = networkLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Recent activity")
            Spacer(Modifier.height(10.dp))

            RadarLogConsole(
                events   = networkEvents,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Hero gradient card ────────────────────────────────────────────────────────

@Composable
private fun DashboardHero(
    score: Float,
    nodeRole: NodeRole,
    isExpertMode: Boolean,
    isUnstable: Boolean
) {
    val isSuperPair  = nodeRole == NodeRole.SUPER_PAIR
    val roleColor    = if (isSuperPair) Color(0xFF0A84FF) else MaterialTheme.colorScheme.onSurfaceVariant
    val roleLabel    = if (isSuperPair) "Group coordinator" else "Group member"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(20.dp))
            .padding(vertical = 28.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text          = "Your file protection",
                style         = MaterialTheme.typography.labelMedium,
                color         = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(16.dp))

            ReliabilityGauge(score = score, size = 152.dp)

            Spacer(Modifier.height(20.dp))

            Surface(
                color  = roleColor.copy(alpha = 0.12f),
                shape  = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, roleColor.copy(alpha = 0.28f))
            ) {
                Text(
                    text       = roleLabel,
                    color      = roleColor,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            if (isExpertMode && isUnstable) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text  = "Unstable connection",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── Section label avec divider ────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text          = text.uppercase(),
            style         = MaterialTheme.typography.labelSmall,
            color         = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}

// ── Storage card ──────────────────────────────────────────────────────────────

@Composable
private fun StorageCard(
    usedBytes: Long,
    allocatedBytes: Long,
    blockCount: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (allocatedBytes > 0L) (usedBytes.toFloat() / allocatedBytes).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "storageProgress",
    )
    val blue   = Color(0xFF0A84FF)
    val green  = Color(0xFF34C759)
    val barColor = if (progress > 0.85f) Color(0xFFFF9F0A) else blue

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = blue,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text  = "Storage shared",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text  = "${formatBytesShort(usedBytes)} / ${formatBytesShort(allocatedBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE5E5EA))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(barColor.copy(alpha = 0.7f), barColor)
                            )
                        )
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = green,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text  = "$blockCount block${if (blockCount != 1) "s" else ""} hosted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text  = "${(progress * 100).toInt()}% used",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress > 0.85f) Color(0xFFFF9F0A) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Formatters ────────────────────────────────────────────────────────────────

private fun formatUptime(uptimeMs: Long): String {
    val hours   = TimeUnit.MILLISECONDS.toHours(uptimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
    return "%02d:%02d".format(hours, minutes)
}

private fun formatBytesShort(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    val mb = bytes / (1024.0 * 1024)
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else      -> "$bytes B"
    }
}
