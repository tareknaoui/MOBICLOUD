package com.mobicloud.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.usecase.m00_identity.CloudLoginUseCase
import com.mobicloud.domain.usecase.m00_identity.CloudRegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CloudAuthState {
    object Idle : CloudAuthState()
    object Loading : CloudAuthState()
    object Success : CloudAuthState()
    data class Error(val message: String) : CloudAuthState()
}

@HiltViewModel
class CloudAuthViewModel @Inject constructor(
    private val cloudLoginUseCase: CloudLoginUseCase,
    private val cloudRegisterUseCase: CloudRegisterUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<CloudAuthState>(CloudAuthState.Idle)
    val state: StateFlow<CloudAuthState> = _state.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = CloudAuthState.Loading
            cloudLoginUseCase(email, password)
                .onSuccess { _state.value = CloudAuthState.Success }
                .onFailure { _state.value = CloudAuthState.Error(it.message ?: "Erreur inconnue") }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _state.value = CloudAuthState.Loading
            cloudRegisterUseCase(email, password)
                .onSuccess { _state.value = CloudAuthState.Success }
                .onFailure { _state.value = CloudAuthState.Error(it.message ?: "Erreur inconnue") }
        }
    }

    fun reset() { _state.value = CloudAuthState.Idle }
}
