package com.mobicloud.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable

@Serializable
object RestoreIdentityRoute

private val IosBlue = Color(0xFF0A84FF)
private val IosText1 = Color(0xFF1C1C1E)
private val IosText2 = Color(0xFF8E8E93)
private val IosGreen = Color(0xFF34C759)
private val IosRed = Color(0xFFFF3B30)

@Composable
fun RestoreIdentityScreen(
    onRestored: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RestoreIdentityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is RestoreState.Success) {
            onRestored()
        }
    }

    if (state is RestoreState.SuccessWithPriorData) {
        val oldHash = (state as RestoreState.SuccessWithPriorData).oldOwnerPubKeyHash
        AlertDialog(
            onDismissRequest = { viewModel.skipDeleteOldData() },
            title = { Text("Données précédentes détectées", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Cet appareil contenait des données liées à une autre identité. " +
                    "Voulez-vous les supprimer définitivement ?",
                    fontSize = 14.sp,
                    color = IosText2,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteOldData(oldHash) },
                    colors = ButtonDefaults.buttonColors(containerColor = IosRed)
                ) {
                    Text("Supprimer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skipDeleteOldData() }) {
                    Text("Conserver", color = IosText2)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (state is RestoreState.Success) Icons.Outlined.CheckCircle else Icons.Outlined.Key,
                contentDescription = null,
                tint = if (state is RestoreState.Success) IosGreen else IosBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Restaurer mon compte",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = IosText1,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Entrez le code de récupération affiché sur votre ancien téléphone. Vos fichiers distribués seront accessibles une fois reconnecté au réseau.",
                fontSize = 14.sp,
                color = IosText2,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    if (state is RestoreState.Error) viewModel.reset()
                },
                label = { Text("Code de récupération") },
                placeholder = { Text("Collez votre code ici") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                isError = state is RestoreState.Error,
                supportingText = {
                    if (state is RestoreState.Error) {
                        Text(
                            text = (state as RestoreState.Error).message,
                            color = IosRed,
                            fontSize = 12.sp
                        )
                    }
                },
                minLines = 3,
                maxLines = 5
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.restore(code) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                enabled = state !is RestoreState.Loading
            ) {
                if (state is RestoreState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Restaurer",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onBack) {
                Text("Retour", color = IosText2, fontSize = 14.sp)
            }
        }
    }
}
