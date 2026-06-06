package com.mobicloud.presentation.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.core.preferences.data.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

sealed class PinLockState {
    object Idle : PinLockState()
    object Success : PinLockState()
    object WrongPin : PinLockState()
}

@HiltViewModel
class PinLockViewModel @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource
) : ViewModel() {

    private val _state = MutableStateFlow<PinLockState>(PinLockState.Idle)
    val state: StateFlow<PinLockState> = _state.asStateFlow()

    fun verify(pin: String) {
        viewModelScope.launch {
            val storedHash = userPreferencesDataSource.observePinHash().first()
            val inputHash = sha256(pin)
            if (inputHash == storedHash) {
                _state.value = PinLockState.Success
            } else {
                _state.value = PinLockState.WrongPin
            }
        }
    }

    fun resetState() {
        _state.value = PinLockState.Idle
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
