package com.mobicloud.presentation.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.data.network.service.MobicloudP2PService
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.SendDepartureNoticeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: NodeSettingsRepository,
    hostedBlockRepository: HostedBlockRepository,
    private val sendDepartureNoticeUseCase: SendDepartureNoticeUseCase
) : AndroidViewModel(application) {

    val settings: StateFlow<NodeSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NodeSettings(0L))

    val usedStorageBytes: StateFlow<Long> = hostedBlockRepository.observeTotalHostedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val freeSpaceBytes: StateFlow<Long> = settingsRepository.observeFreeSpaceBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    // Story 13.1 — toggle Mode Diagnostics Avancés
    val isExpertMode: StateFlow<Boolean> = settingsRepository.observeExpertMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _showWarningDialog = MutableStateFlow(false)
    val showWarningDialog: StateFlow<Boolean> = _showWarningDialog.asStateFlow()

    private val _pendingBytes = MutableStateFlow<Long?>(null)

    fun requestUpdateAllocatedStorage(newBytes: Long) {
        if (newBytes < usedStorageBytes.value) {
            _pendingBytes.value = newBytes
            _showWarningDialog.value = true
        } else {
            viewModelScope.launch { settingsRepository.updateAllocatedStorage(newBytes) }
        }
    }

    fun confirmReduceQuota() {
        val bytes = _pendingBytes.value ?: return
        viewModelScope.launch {
            settingsRepository.updateAllocatedStorage(bytes)
            _showWarningDialog.value = false
            _pendingBytes.value = null
        }
    }

    fun dismissWarningDialog() {
        _showWarningDialog.value = false
        _pendingBytes.value = null
    }

    fun updateExpertMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateExpertMode(enabled) }
    }

    private val _showLeaveDialog = MutableStateFlow(false)
    val showLeaveDialog: StateFlow<Boolean> = _showLeaveDialog.asStateFlow()

    private val _isLeaving = MutableStateFlow(false)
    val isLeaving: StateFlow<Boolean> = _isLeaving.asStateFlow()

    fun requestLeaveCluster() { _showLeaveDialog.value = true }
    fun dismissLeaveDialog() { _showLeaveDialog.value = false }

    fun confirmLeaveCluster() {
        _showLeaveDialog.value = false
        _isLeaving.value = true
        viewModelScope.launch {
            sendDepartureNoticeUseCase.invoke()
            val ctx = getApplication<Application>()
            ctx.stopService(Intent(ctx, MobicloudP2PService::class.java))
            _isLeaving.value = false
        }
    }
}
