package com.mobicloud.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.usecase.m00_identity.ImportIdentityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RestoreState {
    object Idle : RestoreState()
    object Loading : RestoreState()
    object Success : RestoreState()
    data class Error(val message: String) : RestoreState()
}

@HiltViewModel
class RestoreIdentityViewModel @Inject constructor(
    private val importIdentityUseCase: ImportIdentityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val state: StateFlow<RestoreState> = _state.asStateFlow()

    fun restore(code: String) {
        if (code.isBlank()) {
            _state.value = RestoreState.Error("Le code de récupération est vide.")
            return
        }
        viewModelScope.launch {
            _state.value = RestoreState.Loading
            importIdentityUseCase(code.trim())
                .onSuccess {
                    _state.value = RestoreState.Success
                }
                .onFailure { _state.value = RestoreState.Error(it.message ?: "Code invalide.") }
        }
    }

    fun reset() { _state.value = RestoreState.Idle }
}
