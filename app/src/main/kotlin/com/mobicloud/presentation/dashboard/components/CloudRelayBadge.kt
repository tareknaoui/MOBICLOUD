package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        TransferChannelState.DIRECT   -> Triple(Icons.Default.CheckCircle, Color(0xFF34C759), "Direct connection")
        TransferChannelState.RELAY_HA -> Triple(Icons.Default.Cloud,       Color(0xFF0A84FF), "Via server")
        TransferChannelState.OFFLINE  -> Triple(Icons.Default.Warning,     Color(0xFFFF3B30), "Offline")
    }

    Surface(
        color = tint.copy(alpha = 0.10f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Channel: $label",
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}
