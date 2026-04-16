package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Seuils de couleur : Vert Terminal (> 60%), Ambre (> 30%), Rouge (≤ 30%)
private val colorSain = Color(0xFF00FF41)
private val colorAlerte = Color(0xFFFFB300)
private val colorCritique = Color(0xFFFF3333)
private val colorTrack = Color(0xFF333333)

@Composable
fun ReliabilityGauge(
    score: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val scorePercent = (score * 100).toInt().coerceIn(0, 100)
    val arcColor = when {
        score > 0.6f -> colorSain
        score > 0.3f -> colorAlerte
        else -> colorCritique
    }
    val sweepAngle = score.coerceIn(0f, 1f) * 270f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            val startAngle = 135f
            drawArc(
                color = colorTrack,
                startAngle = startAngle,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = stroke
            )
        }
        Text(
            text = "$scorePercent%",
            color = arcColor,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
