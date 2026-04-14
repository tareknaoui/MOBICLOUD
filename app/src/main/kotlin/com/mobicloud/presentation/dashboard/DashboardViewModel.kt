package com.mobicloud.presentation.dashboard

import androidx.lifecycle.ViewModel
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.repository.NetworkServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    networkServiceController: NetworkServiceController
) : ViewModel() {

    val serviceStatus: StateFlow<ServiceStatus> = networkServiceController.serviceStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ServiceStatus.STOPPED)
}
