package com.mobicloud.presentation.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val colorSain     = Color(0xFF34C759)
private val colorAlerte   = Color(0xFFFF9F0A)
private val colorCritique = Color(0xFFFF3B30)
private val colorTrack    = Color(0xFFE5E5EA)

@Composable
fun ReliabilityGauge(
    score: Float,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gauge_score"
    )
    val scorePercent = (score * 100).toInt().coerceIn(0, 100)
    val arcColor = when {
        score > 0.6f -> colorSain
        score > 0.3f -> colorAlerte
        else         -> colorCritique
    }
    val sweepAngle = animatedScore * 270f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = 13.dp.toPx()
            val glowPx   = 24.dp.toPx()
            val stroke    = Stroke(width = strokePx, cap = StrokeCap.Round)
            val glowStroke = Stroke(width = glowPx,  cap = StrokeCap.Round)
            val startAngle = 135f

            drawArc(
                color = colorTrack,
                startAngle = startAngle,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke
            )
            if (animatedScore > 0.02f) {
                drawArc(
                    color = arcColor.copy(alpha = 0.18f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = glowStroke
                )
            }
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$scorePercent%",
                color = arcColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            val qualityLabel = when {
                score > 0.6f -> "Excellent"
                score > 0.3f -> "Good"
                else         -> "Poor"
            }
            Text(
                text = qualityLabel,
                style = MaterialTheme.typography.labelSmall,
                color = arcColor,
                letterSpacing = 0.5.sp
            )
        }
    }
}
