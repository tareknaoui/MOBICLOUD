package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicloud.domain.models.NetworkLogEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val borderColor = Color(0xFF333333)
private val timestampColor = Color(0xFF9E9E9E)
private val messageColor = Color(0xFFE0E0E0)
private val activeColor = Color(0xFF00FF41)
private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun RadarLogConsole(
    events: List<NetworkLogEvent>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, borderColor)
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "RADAR LOG",
                color = activeColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            // Statut textuel statique — pas d'animation clignotante (économie batterie)
            Text(
                text = "[ACTIF]",
                color = activeColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        if (events.isEmpty()) {
            Text(
                text = "> EN ATTENTE D'ÉVÉNEMENTS RÉSEAU_",
                color = timestampColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            // Les événements sont déjà en ordre chronologique inversé (prepend dans NetworkEventRepositoryImpl)
            LazyColumn(
                modifier = Modifier.heightIn(max = 160.dp)
            ) {
                items(events) { event ->
                    Row {
                        Text(
                            text = timeFormat.format(Date(event.timestampMs)) + " ",
                            color = timestampColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = event.message,
                            color = messageColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
