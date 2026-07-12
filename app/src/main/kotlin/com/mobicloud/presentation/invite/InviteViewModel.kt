package com.mobicloud.presentation.invite

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.usecase.m11_join.GenerateClusterInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InviteUiState {
    data object Loading : InviteUiState
    data class Ready(val inviteUri: String, val qrBitmap: Bitmap?) : InviteUiState
    data class Error(val message: String) : InviteUiState
}

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val generateClusterInviteUseCase: GenerateClusterInviteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<InviteUiState>(InviteUiState.Loading)
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    init {
        generate()
    }

    fun generate() {
        _uiState.value = InviteUiState.Loading
        viewModelScope.launch {
            generateClusterInviteUseCase()
                .onSuccess { invite ->
                    val uri = invite.toUri()
                    _uiState.value = InviteUiState.Ready(
                        inviteUri = uri,
                        qrBitmap = QrCodeGenerator.generate(uri)
                    )
                }
                .onFailure { err ->
                    _uiState.value = InviteUiState.Error(
                        err.message ?: "Impossible de générer l'invitation"
                    )
                }
        }
    }
}
