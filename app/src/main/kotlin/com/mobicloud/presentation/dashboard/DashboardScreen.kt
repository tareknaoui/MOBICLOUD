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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val networkEvents by viewModel.networkEvents.collectAsStateWithLifecycle()
    val hasActivePeers by viewModel.hasActivePeers.collectAsStateWithLifecycle()
    val nodeRole by viewModel.nodeRole.collectAsStateWithLifecycle()
    val isNetworkUnstable by viewModel.isNetworkUnstable.collectAsStateWithLifecycle()

    val uptimeFormatted = formatUptime(diagnostics.uptimeMs)
    val networkLabel = when (diagnostics.networkType) {
        NetworkType.WIFI -> "Wifi"
        NetworkType.CELLULAR -> "4G"
        NetworkType.UNKNOWN -> "—"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Score de fiabilité central (AC #1)
        ReliabilityGauge(
            score = diagnostics.reliabilityScore,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Badge rôle du nœud (Story 3.2)
        Text(
            text = if (nodeRole == NodeRole.SUPER_PAIR) "★ Super-Pair" else "● Nœud Connecté",
            color = if (nodeRole == NodeRole.SUPER_PAIR) Color(0xFF00FF41) else Color(0xFF8BC34A),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // AC#6 — Badge "Réseau instable" rouge (Story 3.4 : Circuit-Breaker actif)
        if (isNetworkUnstable) {
            Text(
                text = "⚠ Réseau instable",
                color = Color(0xFFFF1744),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Message si aucun pair détecté (AC #5)
        if (!hasActivePeers) {
            Text(
                text = "Aucun pair détecté — scan en cours...",
                color = Color(0xFFFFB300),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // KPI Cards — grille 2×2 (AC #2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiDiagnosticCard(
                label = "BATTERIE",
                value = "${diagnostics.batteryPercent}%",
                modifier = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label = "UPTIME",
                value = uptimeFormatted,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiDiagnosticCard(
                label = "RÉSEAU",
                value = networkLabel,
                modifier = Modifier.weight(1f)
            )
            KpiDiagnosticCard(
                label = "PAIRS ACTIFS",
                value = "${diagnostics.activePeerCount}",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))

        // Console de logs réseau (AC #3)
        RadarLogConsole(
            events = networkEvents,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatUptime(uptimeMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
    return "%02d:%02d".format(hours, minutes)
}
