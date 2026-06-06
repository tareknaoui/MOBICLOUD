package com.mobicloud.presentation.pin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val IosBlue = Color(0xFF0A84FF)
private val IosBg = Color(0xFFF2F2F7)
private val IosText1 = Color(0xFF1C1C1E)

private val PIN_LENGTH = 4

@Composable
fun PinDots(
    currentLength: Int,
    shakeKey: Int,
    modifier: Modifier = Modifier,
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            listOf(12f, -12f, 8f, -8f, 4f, 0f).forEach { target ->
                offsetX.animateTo(target, tween(60))
            }
        }
    }

    Row(
        modifier = modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PIN_LENGTH) { index ->
            val filled = index < currentLength
            val dotColor by animateColorAsState(
                targetValue = if (filled) IosBlue else Color(0xFFD1D1D6),
                animationSpec = tween(150),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@Composable
fun PinKeyboard(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(72.dp))
                        "⌫" -> Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable(enabled = pin.isNotEmpty()) {
                                    onPinChange(pin.dropLast(1))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                contentDescription = "Supprimer",
                                tint = IosText1,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        else -> Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(IosBg)
                                .clickable(enabled = pin.length < PIN_LENGTH) {
                                    onPinChange(pin + key)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = IosText1,
                            )
                        }
                    }
                }
            }
        }
    }
}
