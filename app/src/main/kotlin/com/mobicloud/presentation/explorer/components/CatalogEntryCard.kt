package com.mobicloud.presentation.explorer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

private data class FileTypeStyle(
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color
)

private fun styleForFile(name: String): FileTypeStyle = when {
    name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)$", RegexOption.IGNORE_CASE)) ->
        FileTypeStyle(Icons.Default.Photo, Color(0xFFFF2D55), Color(0xFFFFEBEB)) // Pink/Red
    name.matches(Regex(".*\\.(mp4|mkv|avi|mov)$", RegexOption.IGNORE_CASE)) ->
        FileTypeStyle(Icons.Default.PlayArrow, Color(0xFF8B5CF6), Color(0xFFF3E8FF)) // Purple
    name.matches(Regex(".*\\.(mp3|aac|flac|wav)$", RegexOption.IGNORE_CASE)) ->
        FileTypeStyle(Icons.Default.MusicNote, Color(0xFF00B0FF), Color(0xFFE0F7FA)) // Cyan
    else ->
        FileTypeStyle(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF43A047), Color(0xFFE8F5E9)) // Green/Emerald
}

private data class BadgeStyle(
    val text: String,
    val dotColor: Color,
    val textColor: Color,
    val bgColor: Color,
    val borderColor: Color
)

private fun badgeStyleFor(availability: AvailabilityState): BadgeStyle = when (availability) {
    AvailabilityState.COMPLET -> BadgeStyle(
        text = "Complete",
        dotColor = Color(0xFF34C759),
        textColor = Color(0xFF1E4620),
        bgColor = Color(0xFFE8F5E9),
        borderColor = Color(0xFFC8E6C9)
    )
    AvailabilityState.PARTIEL -> BadgeStyle(
        text = "Partial",
        dotColor = Color(0xFFFF9F0A),
        textColor = Color(0xFF5D4037),
        bgColor = Color(0xFFFFF3E0),
        borderColor = Color(0xFFFFE0B2)
    )
    AvailabilityState.DEGRADE -> BadgeStyle(
        text = "Degraded",
        dotColor = Color(0xFFFF3B30),
        textColor = Color(0xFF621B16),
        bgColor = Color(0xFFFFEBEE),
        borderColor = Color(0xFFFFCDD2)
    )
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
    val badgeStyle = badgeStyleFor(availability)

    val animatedBadgeColor by animateColorAsState(
        targetValue = badgeStyle.textColor,
        animationSpec = tween(400),
        label = "badgeColor"
    )
    val animatedBadgeBg by animateColorAsState(
        targetValue = badgeStyle.bgColor,
        animationSpec = tween(400),
        label = "badgeBg"
    )
    val animatedBadgeBorder by animateColorAsState(
        targetValue = badgeStyle.borderColor,
        animationSpec = tween(400),
        label = "badgeBorder"
    )

    val dateFormatter = remember { SimpleDateFormat("dd/MM • HH:mm", Locale.getDefault()) }
    val dateFormatted = dateFormatter.format(Date(entry.versionClock))

    val displayName = entry.originalFileName.ifBlank { "${entry.fileHash.take(10)}…" }
    val fileStyle = styleForFile(entry.originalFileName)
    val sizeText = entry.originalFileSize.toReadableSize()

    val downloadInteraction = remember { MutableInteractionSource() }
    val isDownloadPressed by downloadInteraction.collectIsPressedAsState()
    val downloadScale by animateFloatAsState(
        targetValue = if (isDownloadPressed) 0.80f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "downloadScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F1F5)), // Sleek subtle border
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
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
                    .background(fileStyle.bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileStyle.icon,
                    contentDescription = null,
                    tint = fileStyle.iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C1E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${entry.fragmentLocations.size} copies",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF757575)
                    )
                    if (sizeText.isNotEmpty()) {
                        Text(text = "•", color = Color(0xFFB0BEC5), fontSize = 10.sp)
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575)
                        )
                    }
                    Text(text = "•", color = Color(0xFFB0BEC5), fontSize = 10.sp)
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF757575)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = animatedBadgeBg,
                border = BorderStroke(1.dp, animatedBadgeBorder),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(badgeStyle.dotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeStyle.text,
                        color = animatedBadgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (onDownload != null && availability != AvailabilityState.DEGRADE) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onDownload(entry.fileHash) },
                    interactionSource = downloadInteraction,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFF2F4F7), CircleShape) // Sleek circular container
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = downloadScale
                                scaleY = downloadScale
                            }
                    )
                }
            }
        }
    }
}
