package com.mobicloud.presentation.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val gossipSyncUseCase: GossipSyncUseCase
) : ViewModel() {

    val catalogEntries: StateFlow<List<CatalogEntry>> = catalogRepository.getAllEntriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshCatalog() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                gossipSyncUseCase.runGossipCycle()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
