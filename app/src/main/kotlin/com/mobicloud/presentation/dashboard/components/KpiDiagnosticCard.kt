package com.mobicloud.presentation.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val borderColor = Color(0xFF333333)
private val labelColor = Color(0xFF9E9E9E)
private val dataColor = Color(0xFFE0E0E0)

@Composable
fun KpiDiagnosticCard(
    label: String,
    value: String,
    accentColor: Color = Color(0xFF00FF41),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .border(1.dp, borderColor)
            .height(72.dp)
    ) {
        // Liseré coloré vertical gauche (état en un coup d'œil)
        Column(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor)
        ) {}
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 12.sp
            )
            Text(
                text = value,
                color = dataColor,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
