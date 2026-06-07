package com.mobicloud.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable

@Serializable
object CloudAuthRoute

private val IosBlue = Color(0xFF0A84FF)
private val IosText1 = Color(0xFF1C1C1E)
private val IosText2 = Color(0xFF8E8E93)
private val IosRed = Color(0xFFFF3B30)

@Composable
fun CloudAuthScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudAuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }

    LaunchedEffect(state) {
        if (state is CloudAuthState.Success) onSuccess()
    }

    Scaffold(modifier = modifier, containerColor = Color.White) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = IosBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = if (isLoginMode) "Restore my account" else "Create a cloud account",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = IosText1,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isLoginMode)
                    "Enter the credentials you used on your previous device to restore your identity and files."
                else
                    "Create an account to back up your identity to the cloud. You can restore it on a new device.",
                fontSize = 13.sp,
                color = IosText2,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.reset() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                isError = state is CloudAuthState.Error
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; viewModel.reset() },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                isError = state is CloudAuthState.Error,
                supportingText = {
                    if (state is CloudAuthState.Error) {
                        Text(
                            text = (state as CloudAuthState.Error).message,
                            color = IosRed,
                            fontSize = 12.sp
                        )
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isLoginMode) viewModel.login(email, password)
                    else viewModel.register(email, password)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                enabled = state !is CloudAuthState.Loading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (state is CloudAuthState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (isLoginMode) "Restore" else "Create account",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isLoginMode) "No account yet?" else "Already have an account?",
                    fontSize = 13.sp,
                    color = IosText2
                )
                TextButton(onClick = {
                    isLoginMode = !isLoginMode
                    viewModel.reset()
                }) {
                    Text(
                        text = if (isLoginMode) "Sign up" else "Sign in",
                        fontSize = 13.sp,
                        color = IosBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            TextButton(onClick = onBack) {
                Text("Back", color = IosText2, fontSize = 14.sp)
            }
        }
    }
}
