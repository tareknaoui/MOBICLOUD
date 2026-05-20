package com.mobicloud.presentation.explorer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private fun fileIconFor(name: String): ImageVector = when {
    name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)$", RegexOption.IGNORE_CASE)) -> Icons.Default.Photo
    name.matches(Regex(".*\\.(mp4|mkv|avi|mov)$", RegexOption.IGNORE_CASE)) -> Icons.Default.PlayArrow
    name.matches(Regex(".*\\.(mp3|aac|flac|wav)$", RegexOption.IGNORE_CASE)) -> Icons.Default.MusicNote
    else -> Icons.Default.InsertDriveFile
}

private fun Long.toReadableSize(): String = when {
    this <= 0L -> ""
    this < 1024 -> "$this o"
    this < 1024 * 1024 -> "${this / 1024} Ko"
    else -> String.format("%.1f Mo", this / (1024.0 * 1024.0))
}

@Composable
fun CatalogEntryCard(
    entry: CatalogEntry,
    modifier: Modifier = Modifier,
    onDownload: ((String) -> Unit)? = null
) {
    val availability = entry.availabilityState()
    val (badgeText, badgeColor, badgeBg) = when (availability) {
        AvailabilityState.COMPLET -> Triple("Complet", Color(0xFF34C759), Color(0xFFE8F5E9))
        AvailabilityState.PARTIEL -> Triple("Partiel", Color(0xFFFF9F0A), Color(0xFFFFF3E0))
        AvailabilityState.DEGRADE -> Triple("Dégradé", Color(0xFFFF3B30), Color(0xFFFFEBEE))
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM • HH:mm", Locale.getDefault()) }
    val dateFormatted = dateFormatter.format(Date(entry.versionClock))

    val displayName = entry.originalFileName.ifBlank { "${entry.fileHash.take(10)}…" }
    val fileIcon = fileIconFor(entry.originalFileName)
    val sizeText = entry.originalFileSize.toReadableSize()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFE8EFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = null,
                    tint = Color(0xFF0A84FF),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1C1C1E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${entry.fragmentLocations.size} copies",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E8E93)
                    )
                    if (sizeText.isNotEmpty()) {
                        Text(text = "·", color = Color(0xFF8E8E93), fontSize = 10.sp)
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8E8E93)
                        )
                    }
                    Text(text = "·", color = Color(0xFF8E8E93), fontSize = 10.sp)
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = badgeBg
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (onDownload != null && availability != AvailabilityState.DEGRADE) {
                Spacer(modifier = Modifier.width(0.dp))
                IconButton(
                    onClick = { onDownload(entry.fileHash) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Télécharger",
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
