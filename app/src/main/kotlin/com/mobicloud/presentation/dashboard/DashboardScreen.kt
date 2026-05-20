package com.mobicloud.presentation.dashboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.domain.models.NetworkType
import com.mobicloud.domain.models.NodeRole
import com.mobicloud.domain.models.TransferChannelState
import com.mobicloud.domain.models.m11_join.MAX_CLUSTER_SIZE
import com.mobicloud.presentation.dashboard.components.CloudRelayBadge
import com.mobicloud.presentation.dashboard.components.HealthBanner
import com.mobicloud.presentation.dashboard.components.HealthState
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
    val hasActivePeers  by viewModel.hasActivePeers.collectAsStateWithLifecycle()
    val nodeRole        by viewModel.nodeRole.collectAsStateWithLifecycle()
    val isNetworkUnstable by viewModel.isNetworkUnstable.collectAsStateWithLifecycle()
    val relayState      by viewModel.relayState.collectAsStateWithLifecycle()

    val isExpertMode    by viewModel.isExpertMode.collectAsStateWithLifecycle()
    val communitySize   by viewModel.communitySize.collectAsStateWithLifecycle()
    val allocatedBytes  by viewModel.allocatedStorageBytes.collectAsStateWithLifecycle()
    val hostedBlockCount by viewModel.hostedBlockCount.collectAsStateWithLifecycle()

    val uptimeFormatted = formatUptime(diagnostics.uptimeMs)
    val networkLabel = when (diagnostics.networkType) {
        NetworkType.WIFI     -> "Wifi"
        NetworkType.CELLULAR -> "4G"
        NetworkType.UNKNOWN  -> "—"
    }

    val healthState by remember {
        derivedStateOf {
            val reliability = (diagnostics.reliabilityScore * 100).toInt()
            val peerCount   = diagnostics.activePeerCount
            when {
                !hasActivePeers                                                  -> HealthState.Searching
                reliability < 40 || isNetworkUnstable                           -> HealthState.Degraded(peerCount)
                reliability >= 70 && relayState == TransferChannelState.DIRECT  -> HealthState.Healthy(peerCount, networkLabel)
                else                                                             -> HealthState.Slow(peerCount, networkLabel)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Health banner (Mode Simple uniquement) ──
        if (!isExpertMode) {
            Spacer(Modifier.height(4.dp))
            HealthBanner(state = healthState)
            Spacer(Modifier.height(12.dp))
        }

        // ── Hero gradient card ──
        DashboardHero(
            score        = diagnostics.reliabilityScore,
            nodeRole     = nodeRole,
            relayState   = relayState,
            isExpertMode = isExpertMode,
            isUnstable   = isNetworkUnstable
        )

        Spacer(Modifier.height(20.dp))

        // ── Section Aperçu ──
        SectionLabel("Aperçu")
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiDiagnosticCard(
                label       = "Batterie",
                value       = "${diagnostics.batteryPercent}%",
                hint        = "Impact app : minime",
                icon        = Icons.Filled.BatteryFull,
                accentColor = Color(0xFF0A84FF),
                modifier    = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label       = "Mon groupe",
                value       = "$communitySize/$MAX_CLUSTER_SIZE",
                hint        = "Membres connectés",
                icon        = Icons.Filled.Group,
                accentColor = Color(0xFF34C759),
                modifier    = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiDiagnosticCard(
                label       = "Ma contribution",
                value       = formatBytesShort(allocatedBytes),
                hint        = "Espace que je partage",
                icon        = Icons.Filled.Storage,
                accentColor = Color(0xFF0A84FF),
                modifier    = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label       = "Fichiers protégés",
                value       = "$hostedBlockCount",
                hint        = "Sauvegardés",
                icon        = Icons.Filled.Folder,
                accentColor = Color(0xFF34C759),
                modifier    = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Toggle détails avancés ──
        TextButton(
            onClick  = { viewModel.toggleExpertMode() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text  = if (isExpertMode) "↑ Masquer les infos avancées" else "→ Infos avancées",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0A84FF)
            )
        }

        // ── Mode Expert ──
        if (isExpertMode) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Infos avancées")
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiDiagnosticCard(
                    label    = "Durée de session",
                    value    = uptimeFormatted,
                    modifier = Modifier.weight(1f)
                )
                KpiDiagnosticCard(
                    label    = "Réseau utilisé",
                    value    = networkLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Activité récente")
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
    relayState: TransferChannelState,
    isExpertMode: Boolean,
    isUnstable: Boolean
) {
    val isSuperPair = nodeRole == NodeRole.SUPER_PAIR
    val roleColor   = if (isSuperPair) Color(0xFF0A84FF) else MaterialTheme.colorScheme.onSurfaceVariant
    val roleLabel   = if (isSuperPair) "Vous organisez ce groupe" else "Membre du groupe"

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
                text          = "Protection de vos fichiers",
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

            Spacer(Modifier.height(10.dp))
            CloudRelayBadge(state = relayState)

            if (isExpertMode && isUnstable) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text  = "Connexion instable",
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
