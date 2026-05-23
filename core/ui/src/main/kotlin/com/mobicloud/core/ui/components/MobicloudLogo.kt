/*
 * Copyright 2025 Mobicloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobicloud.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Branded logo header: cloud+checkmark icon, app name, and tagline.
 * Matches the Mobicloud Friendly design reference.
 */
@Composable
fun MobicloudLogoHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MobicloudCloudIcon()
        Text(
            text = "Mobicloud",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Safe. Everywhere. Always.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MobicloudCloudIcon() {
    Canvas(modifier = Modifier.size(120.dp, 80.dp)) {
        val w = size.width
        val h = size.height
        val mauve = Color(0xFF0A84FF)

        val cloud = Path().apply {
            moveTo(w * 0.14f, h * 0.78f)
            quadraticTo(w * 0.04f, h * 0.78f, w * 0.04f, h * 0.58f)
            quadraticTo(w * 0.04f, h * 0.40f, w * 0.20f, h * 0.40f)
            quadraticTo(w * 0.20f, h * 0.18f, w * 0.42f, h * 0.22f)
            quadraticTo(w * 0.48f, h * 0.04f, w * 0.64f, h * 0.16f)
            quadraticTo(w * 0.74f, h * 0.04f, w * 0.86f, h * 0.18f)
            quadraticTo(w * 0.97f, h * 0.20f, w * 0.97f, h * 0.42f)
            quadraticTo(w * 0.97f, h * 0.78f, w * 0.84f, h * 0.78f)
            close()
        }
        drawPath(cloud, mauve)

        val checkStroke = Stroke(
            width = 7.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val check = Path().apply {
            moveTo(w * 0.32f, h * 0.55f)
            lineTo(w * 0.46f, h * 0.70f)
            lineTo(w * 0.70f, h * 0.34f)
        }
        drawPath(check, Color.White, style = checkStroke)
    }
}

/**
 * Full-screen decorative background blobs — drawn behind screen content.
 * Use inside a Box as the first child.
 */
@Composable
fun MobicloudBackgroundBlobs(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = Color(0x220A84FF),
            radius = 180.dp.toPx(),
            center = Offset(-60.dp.toPx(), -60.dp.toPx()),
        )
        drawCircle(
            color = Color(0x1534C759),
            radius = 220.dp.toPx(),
            center = Offset(size.width + 80.dp.toPx(), size.height + 80.dp.toPx()),
        )
        drawCircle(
            color = Color(0x110A84FF),
            radius = 140.dp.toPx(),
            center = Offset(size.width * 0.8f, size.height * 0.3f),
        )
    }
}
