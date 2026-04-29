package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobicloud.domain.models.TransferChannelState

@Composable
fun CloudRelayBadge(
    state: TransferChannelState,
    modifier: Modifier = Modifier
) {
    val (icon, tint, label) = when (state) {
        TransferChannelState.DIRECT   -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "P2P Direct")
        TransferChannelState.RELAY_HA -> Triple(Icons.Default.Cloud,       Color(0xFF2196F3), "Relay HA")
        TransferChannelState.OFFLINE  -> Triple(Icons.Default.Warning,     Color(0xFFF44336), "Hors-ligne")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Canal de transfert : $label",
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
