package com.mobicloud.presentation.explorer.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicloud.domain.models.CatalogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AvailabilityState { COMPLET, PARTIEL, DEGRADE }

fun CatalogEntry.availabilityState(): AvailabilityState {
    if (fragmentLocations.isEmpty()) return AvailabilityState.DEGRADE
    val withNodes = fragmentLocations.count { it.nodeIds.isNotEmpty() }
    return when {
        withNodes == fragmentLocations.size -> AvailabilityState.COMPLET
        withNodes == 0 -> AvailabilityState.DEGRADE
        else -> AvailabilityState.PARTIEL
    }
}

@Composable
fun CatalogEntryCard(
    entry: CatalogEntry,
    modifier: Modifier = Modifier,
    onDownload: ((String) -> Unit)? = null
) {
    val (badgeText, badgeColor) = when (entry.availabilityState()) {
        AvailabilityState.COMPLET -> "Complet" to Color(0xFF00FF41)
        AvailabilityState.PARTIEL -> "Partiel" to Color(0xFFFFB300)
        AvailabilityState.DEGRADE -> "Dégradé" to Color(0xFFFF3333)
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val dateFormatted = dateFormatter.format(Date(entry.versionClock))

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(width = 1.dp, color = Color(0xFF333333), shape = RoundedCornerShape(4.dp)),
        color = Color(0xFF000000),
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.fileHash.take(12)}...",
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${entry.fragmentLocations.size} blocs",
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = dateFormatted,
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
            onDownload?.let { callback ->
                if (entry.availabilityState() != AvailabilityState.DEGRADE) {
                    TextButton(onClick = { callback(entry.fileHash) }) {
                        Text("↓", color = badgeColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
