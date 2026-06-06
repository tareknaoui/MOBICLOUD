package com.mobicloud.presentation.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlinx.serialization.Serializable

@Serializable
object PinLockRoute

private val IosBlue = Color(0xFF0A84FF)
private val IosText1 = Color(0xFF1C1C1E)
private val IosText2 = Color(0xFF8E8E93)
private val IosRed = Color(0xFFFF3B30)

@Composable
fun PinLockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinLockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var shakeKey by remember { mutableStateOf(0) }

    LaunchedEffect(state) {
        when (state) {
            is PinLockState.Success -> onUnlocked()
            is PinLockState.WrongPin -> {
                shakeKey++
                pin = ""
            }
            else -> Unit
        }
    }

    // Auto-vérification dès que 4 chiffres sont saisis
    LaunchedEffect(pin) {
        if (pin.length == 4) viewModel.verify(pin)
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
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = IosBlue,
                modifier = Modifier.size(56.dp),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Entrez votre code",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = IosText1,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (state is PinLockState.WrongPin) "Code incorrect" else "MobiCloud",
                fontSize = 14.sp,
                color = if (state is PinLockState.WrongPin) IosRed else IosText2,
            )

            Spacer(Modifier.height(40.dp))

            PinDots(
                currentLength = pin.length,
                shakeKey = shakeKey,
            )

            Spacer(Modifier.height(48.dp))

            PinKeyboard(
                pin = pin,
                onPinChange = { newPin ->
                    if (state is PinLockState.WrongPin) viewModel.resetState()
                    pin = newPin
                },
            )
        }
    }
}
