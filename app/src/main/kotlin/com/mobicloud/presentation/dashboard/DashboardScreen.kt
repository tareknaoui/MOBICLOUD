package com.mobicloud.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val networkEvents by viewModel.networkEvents.collectAsStateWithLifecycle()
    val hasActivePeers by viewModel.hasActivePeers.collectAsStateWithLifecycle()
    val nodeRole by viewModel.nodeRole.collectAsStateWithLifecycle()
    val isNetworkUnstable by viewModel.isNetworkUnstable.collectAsStateWithLifecycle()
    val relayState by viewModel.relayState.collectAsStateWithLifecycle()

    val isExpertMode by viewModel.isExpertMode.collectAsStateWithLifecycle()
    val communitySize by viewModel.communitySize.collectAsStateWithLifecycle()
    val allocatedBytes by viewModel.allocatedStorageBytes.collectAsStateWithLifecycle()
    val hostedBlockCount by viewModel.hostedBlockCount.collectAsStateWithLifecycle()

    val uptimeFormatted = formatUptime(diagnostics.uptimeMs)
    val networkLabel = when (diagnostics.networkType) {
        NetworkType.WIFI -> "Wifi"
        NetworkType.CELLULAR -> "4G"
        NetworkType.UNKNOWN -> "—"
    }

    // Story 13.1 — Calcul du HealthState (Mode Simple uniquement, mais évalué dans les deux modes)
    val healthState = remember(
        diagnostics.reliabilityScore,
        hasActivePeers,
        networkLabel,
        relayState,
        isNetworkUnstable,
        diagnostics.activePeerCount
    ) {
        derivedStateOf {
            val reliability = (diagnostics.reliabilityScore * 100).toInt()
            val peerCount = diagnostics.activePeerCount
            when {
                !hasActivePeers -> HealthState.Searching
                reliability < 40 || isNetworkUnstable -> HealthState.Degraded(peerCount)
                reliability >= 70 && relayState == TransferChannelState.DIRECT ->
                    HealthState.Healthy(peerCount, networkLabel)
                else -> HealthState.Slow(peerCount, networkLabel)
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
        // === Story 13.1 — Bannière santé (Mode Simple uniquement) ===
        if (!isExpertMode) {
            Spacer(Modifier.height(4.dp))
            HealthBanner(state = healthState.value)
        }

        // === Jauge de fiabilité — TOUJOURS visible (Simple + Expert) ===
        ReliabilityGauge(
            score = diagnostics.reliabilityScore,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // === Badge rôle — sémantique humaine (Story 13.1 AC2) ===
        Text(
            text = if (nodeRole == NodeRole.SUPER_PAIR) "★ Coordinateur de Réseau" else "● Membre actif",
            style = MaterialTheme.typography.labelLarge,
            color = if (nodeRole == NodeRole.SUPER_PAIR) Color(0xFF00FF41) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        CloudRelayBadge(
            state = relayState,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Alerte "Connexion lente" — Expert uniquement (en Simple, la bannière s'en charge)
        if (isExpertMode && isNetworkUnstable) {
            Text(
                text = "⚠ Connexion lente",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // === Section "Aperçu" — 4 KPIs sémantiques (Story 13.1 AC4) ===
        SectionLabel("Aperçu")
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiDiagnosticCard(
                label = "BATTERIE",
                value = "${diagnostics.batteryPercent}%",
                hint = "Impact app : minime",
                modifier = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label = "COMMUNAUTÉ",
                value = "$communitySize/$MAX_CLUSTER_SIZE",
                hint = "Membres connectés",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiDiagnosticCard(
                label = "MA CONTRIBUTION",
                value = formatBytesShort(allocatedBytes),
                hint = "Espace que je partage",
                modifier = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label = "FICHIERS PROTÉGÉS",
                value = "$hostedBlockCount",
                hint = "Sauvegardés ✓",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // === Toggle Détails techniques (Story 13.1 AC5) ===
        OutlinedButton(
            onClick = { viewModel.toggleExpertMode() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isExpertMode) "▴ MASQUER DÉTAILS TECHNIQUES" else "▾ DÉTAILS TECHNIQUES",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // === Mode Expert : KPIs techniques + RadarLog (Story 13.1 AC5) ===
        if (isExpertMode) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Diagnostic technique")
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiDiagnosticCard(
                    label = "UPTIME",
                    value = uptimeFormatted,
                    modifier = Modifier.weight(1f)
                )
                KpiDiagnosticCard(
                    label = "RÉSEAU",
                    value = networkLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Activité réseau")
            Spacer(Modifier.height(8.dp))

            RadarLogConsole(
                events = networkEvents,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
    }
}

private fun formatUptime(uptimeMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
    return "%02d:%02d".format(hours, minutes)
}

private fun formatBytesShort(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    val mb = bytes / (1024.0 * 1024)
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else -> "$bytes B"
    }
}
