package com.mobicloud.presentation.explorer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobicloud.presentation.explorer.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssembledBottomSheet(
    state: DownloadState.Assembled,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0D0D)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "✓ Fichier récupéré en ${state.durationMs}ms — depuis ${state.nodeCount} nœud${if (state.nodeCount > 1) "s" else ""}",
                color = Color(0xFF00FF41),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = state.filePath.takeLast(40),
                color = Color(0xFF9E9E9E),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpen(state.filePath) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF41))
                ) {
                    Text(
                        text = "Ouvrir",
                        color = Color(0xFF000000),
                        fontFamily = FontFamily.Monospace
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Fermer",
                        color = Color(0xFF9E9E9E),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
