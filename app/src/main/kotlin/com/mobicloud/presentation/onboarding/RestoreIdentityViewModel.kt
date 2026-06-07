package com.mobicloud.presentation.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.repository.CatalogRepository
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
    /** L'appareil avait une ancienne identité : proposer la suppression de ses données. */
    data class SuccessWithPriorData(val oldOwnerPubKeyHash: String) : RestoreState()
    data class Error(val message: String) : RestoreState()
}

@HiltViewModel
class RestoreIdentityViewModel @Inject constructor(
    private val importIdentityUseCase: ImportIdentityUseCase,
    private val catalogRepository: CatalogRepository
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
                .onSuccess { result ->
                    _state.value = if (result.previousOwnerPubKeyHash != null) {
                        RestoreState.SuccessWithPriorData(result.previousOwnerPubKeyHash)
                    } else {
                        RestoreState.Success
                    }
                }
                .onFailure { _state.value = RestoreState.Error(it.message ?: "Code invalide.") }
        }
    }

    fun deleteOldData(oldOwnerPubKeyHash: String) {
        viewModelScope.launch {
            // Purge best-effort : un échec ne bloque pas la navigation (l'identité est déjà restaurée),
            // mais il est journalisé pour ne pas masquer un problème de persistance.
            catalogRepository.deleteAllByOwner(oldOwnerPubKeyHash)
                .onFailure { Log.e("RestoreIdentity", "Échec purge données ancienne identité", it) }
            _state.value = RestoreState.Success
        }
    }

    fun skipDeleteOldData() {
        _state.value = RestoreState.Success
    }

    fun reset() { _state.value = RestoreState.Idle }
}
