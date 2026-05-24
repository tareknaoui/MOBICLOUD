package com.mobicloud.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable

@Serializable
object InitRoute

private val Amber = Color(0xFFFFB300)

@Composable
fun InitScreen(
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InitViewModel = hiltViewModel(),
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startInit()
    }

    LaunchedEffect(currentStep) {
        if (currentStep is InitStep.Done) {
            onNavigateToDashboard()
        }
    }

    val statusText = when (currentStep) {
        is InitStep.CreatingIdentity -> "Creating your secure identity…"
        is InitStep.SearchingMembers -> "Searching for nearby members…"
        is InitStep.ConnectingToGroup -> "Connecting to your group…"
        is InitStep.Done -> "Ready!"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "MobiCloud",
            color = Amber,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Stockage distribué sécurisé",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(48.dp))

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = Amber,
            trackColor = Color.White.copy(alpha = 0.1f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = statusText,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}
