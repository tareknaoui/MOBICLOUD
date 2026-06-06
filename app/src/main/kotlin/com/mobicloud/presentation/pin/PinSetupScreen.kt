package com.mobicloud.presentation.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class PinSetupRoute(val fromOnboarding: Boolean = false)

private val IosBlue = Color(0xFF0A84FF)
private val IosText1 = Color(0xFF1C1C1E)
private val IosText2 = Color(0xFF8E8E93)
private val IosRed = Color(0xFFFF3B30)

@Composable
fun PinSetupScreen(
    onDone: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var shakeKey by remember { mutableStateOf(0) }

    LaunchedEffect(state) {
        when (state) {
            is PinSetupState.Success -> onDone()
            is PinSetupState.Mismatch -> {
                shakeKey++
                pin = ""
                delay(400)
                viewModel.retry()
            }
            else -> pin = ""
        }
    }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            when (state) {
                is PinSetupState.EnterNew -> viewModel.onFirstPinEntered(pin)
                is PinSetupState.Confirm -> viewModel.onConfirmPinEntered(pin)
                else -> Unit
            }
        }
    }

    val title = when (state) {
        is PinSetupState.Confirm -> "Confirmez le code"
        is PinSetupState.Mismatch -> "Codes différents"
        else -> "Choisissez un code"
    }
    val subtitle = when (state) {
        is PinSetupState.Confirm -> "Saisissez à nouveau votre code"
        is PinSetupState.Mismatch -> "Recommencez depuis le début"
        else -> "Code à 4 chiffres pour déverrouiller l'app"
    }

    Scaffold(modifier = modifier, containerColor = Color.White) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = IosBlue,
                modifier = Modifier.size(56.dp),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = IosText1,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = if (state is PinSetupState.Mismatch) IosRed else IosText2,
            )

            Spacer(Modifier.height(40.dp))

            PinDots(
                currentLength = pin.length,
                shakeKey = shakeKey,
            )

            Spacer(Modifier.height(48.dp))

            PinKeyboard(pin = pin, onPinChange = { pin = it })

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onSkip) {
                Text("Ignorer", color = IosText2, fontSize = 14.sp)
            }
        }
    }
}
