package com.mobicloud.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.m11_join.ClusterInvite
import com.mobicloud.domain.usecase.m11_join.JoinClusterViaInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface JoinInviteUiState {
    data object Idle : JoinInviteUiState
    data object Joining : JoinInviteUiState
    data class Success(val joinedIntendedCluster: Boolean) : JoinInviteUiState
    data class Error(val message: String) : JoinInviteUiState
}

@HiltViewModel
class JoinInviteViewModel @Inject constructor(
    private val joinClusterViaInviteUseCase: JoinClusterViaInviteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<JoinInviteUiState>(JoinInviteUiState.Idle)
    val uiState: StateFlow<JoinInviteUiState> = _uiState.asStateFlow()

    fun join(clusterId: String, hintedSpNodeId: String) {
        if (_uiState.value == JoinInviteUiState.Joining) return
        _uiState.value = JoinInviteUiState.Joining
        viewModelScope.launch {
            joinClusterViaInviteUseCase(ClusterInvite(clusterId, hintedSpNodeId)).collect { result ->
                result.fold(
                    onSuccess = { r ->
                        _uiState.value = JoinInviteUiState.Success(r.joinedIntendedCluster)
                    },
                    onFailure = { err ->
                        _uiState.value = JoinInviteUiState.Error(
                            err.message ?: "Couldn't join this group"
                        )
                    }
                )
            }
        }
    }
}
