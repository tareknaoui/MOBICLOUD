package com.mobicloud.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    val deletedEntries: StateFlow<List<CatalogEntry>> =
        catalogRepository.getDeletedEntriesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun restoreEntry(fileHash: String) {
        viewModelScope.launch { catalogRepository.restoreFromTrash(fileHash) }
    }

    fun permanentlyDelete(fileHash: String) {
        viewModelScope.launch { catalogRepository.permanentlyDelete(fileHash) }
    }

    fun emptyTrash() {
        viewModelScope.launch { catalogRepository.emptyTrash() }
    }
}
