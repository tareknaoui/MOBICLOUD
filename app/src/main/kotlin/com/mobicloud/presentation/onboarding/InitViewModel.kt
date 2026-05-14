package com.mobicloud.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.repository.NetworkServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed class InitStep {
    object CreatingIdentity : InitStep()
    object SearchingMembers : InitStep()
    object ConnectingToGroup : InitStep()
    object Done : InitStep()
}

private const val INIT_TIMEOUT_MS = 8_000L
private const val STEP_DELAY_MS = 2_000L

@HiltViewModel
class InitViewModel @Inject constructor(
    private val networkServiceController: NetworkServiceController,
) : ViewModel() {

    private val _currentStep = MutableStateFlow<InitStep>(InitStep.CreatingIdentity)
    val currentStep: StateFlow<InitStep> = _currentStep.asStateFlow()

    // F1 fix: guard contre les appels multiples (rotation d'écran → LaunchedEffect relanċé)
    @Volatile private var initStarted = false

    fun startInit() {
        if (initStarted) return
        initStarted = true
        viewModelScope.launch {
            withTimeoutOrNull(INIT_TIMEOUT_MS) {
                _currentStep.value = InitStep.CreatingIdentity
                delay(STEP_DELAY_MS)

                _currentStep.value = InitStep.SearchingMembers
                delay(STEP_DELAY_MS)

                _currentStep.value = InitStep.ConnectingToGroup
                networkServiceController.startService()
                delay(STEP_DELAY_MS)
            }
            _currentStep.value = InitStep.Done
        }
    }
}
