package com.mobicloud.presentation.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.core.preferences.data.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

sealed class PinSetupState {
    object EnterNew : PinSetupState()
    object Confirm : PinSetupState()
    object Success : PinSetupState()
    object Mismatch : PinSetupState()
}

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource
) : ViewModel() {

    private val _state = MutableStateFlow<PinSetupState>(PinSetupState.EnterNew)
    val state: StateFlow<PinSetupState> = _state.asStateFlow()

    private var firstPin: String = ""

    fun onFirstPinEntered(pin: String) {
        firstPin = pin
        _state.value = PinSetupState.Confirm
    }

    fun onConfirmPinEntered(pin: String) {
        if (pin == firstPin) {
            viewModelScope.launch {
                userPreferencesDataSource.setPin(sha256(pin))
                _state.value = PinSetupState.Success
            }
        } else {
            firstPin = ""
            _state.value = PinSetupState.Mismatch
        }
    }

    fun retry() {
        firstPin = ""
        _state.value = PinSetupState.EnterNew
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
