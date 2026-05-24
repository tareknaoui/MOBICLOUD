package com.mobicloud.presentation.join

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.domain.models.m11_join.NodeJoinState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JoinOverlay(
    modifier: Modifier = Modifier,
    viewModel: JoinOverlayViewModel = hiltViewModel()
) {
    val joinState by viewModel.joinState.collectAsStateWithLifecycle()

    val isTransitional = joinState !is NodeJoinState.Member &&
        joinState !is NodeJoinState.SuperPair

    val message = when (joinState) {
        is NodeJoinState.Undiscovered -> "Searching for a group…"
        is NodeJoinState.Joining      -> "Connecting to cluster…"
        is NodeJoinState.Isolated     -> "Retrying…"
        is NodeJoinState.Rejoining    -> "Recovering connection…"
        else                          -> ""
    }

    AnimatedVisibility(
        visible = isTransitional,
        enter = fadeIn(animationSpec = tween(300)),
        exit  = fadeOut(animationSpec = tween(700)),
        modifier = modifier
    ) {
        val primary = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.07f),
                            Color.White,
                        ),
                        radius = 1200f,
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NetworkCanvas(primaryColor = primary)

                Spacer(Modifier.height(44.dp))

                Text(
                    text = "MobiCloud",
                    color = primary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = message,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "joinMessage",
                ) { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFF8E8E93),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkCanvas(primaryColor: Color) {
    val infinite = rememberInfiniteTransition(label = "network")

    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val rotation2 by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(13000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation2",
    )

    val pulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val lineAlpha by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lineAlpha",
    )

    Canvas(modifier = Modifier.size(250.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbitRadius1 = size.minDimension * 0.27f
        val orbitRadius2 = size.minDimension * 0.44f
        val nodeRadius = 5.5.dp.toPx()
        val centerDot = 13.dp.toPx()

        // Subtle orbit guides
        drawCircle(
            color = primaryColor.copy(alpha = 0.07f),
            radius = orbitRadius2,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.1f),
            radius = orbitRadius1,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        // Inner 3 nodes
        val innerNodes = listOf(0f, 120f, 240f).map { angle ->
            val rad = Math.toRadians((angle + rotation).toDouble())
            Offset(
                center.x + orbitRadius1 * cos(rad).toFloat(),
                center.y + orbitRadius1 * sin(rad).toFloat(),
            )
        }

        // Outer 4 nodes
        val outerNodes = listOf(0f, 90f, 180f, 270f).map { angle ->
            val rad = Math.toRadians((angle + rotation2).toDouble())
            Offset(
                center.x + orbitRadius2 * cos(rad).toFloat(),
                center.y + orbitRadius2 * sin(rad).toFloat(),
            )
        }

        // Lines: center → inner
        innerNodes.forEach { node ->
            drawLine(
                color = primaryColor.copy(alpha = lineAlpha),
                start = center,
                end = node,
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Lines: inner → outer (nearest)
        innerNodes.forEachIndexed { i, innerNode ->
            drawLine(
                color = primaryColor.copy(alpha = lineAlpha * 0.55f),
                start = innerNode,
                end = outerNodes[i % outerNodes.size],
                strokeWidth = 0.9.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Outer nodes: glow + dot
        outerNodes.forEach { pos ->
            drawCircle(color = primaryColor.copy(alpha = 0.15f), radius = nodeRadius * 1.8f, center = pos)
            drawCircle(color = primaryColor, radius = nodeRadius * 0.55f, center = pos)
        }

        // Inner nodes: glow + dot
        innerNodes.forEach { pos ->
            drawCircle(color = primaryColor.copy(alpha = 0.22f), radius = nodeRadius * 2f, center = pos)
            drawCircle(color = primaryColor, radius = nodeRadius * 0.75f, center = pos)
        }

        // Center node: multi-ring glow + pulsing core
        drawCircle(color = primaryColor.copy(alpha = 0.07f), radius = centerDot * pulse * 2.2f, center = center)
        drawCircle(color = primaryColor.copy(alpha = 0.15f), radius = centerDot * pulse * 1.5f, center = center)
        drawCircle(color = primaryColor.copy(alpha = 0.35f), radius = centerDot * pulse, center = center)
        drawCircle(color = primaryColor, radius = centerDot * 0.6f * pulse, center = center)
    }
}
