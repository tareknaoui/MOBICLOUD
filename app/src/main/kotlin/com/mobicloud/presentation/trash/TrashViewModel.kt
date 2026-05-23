package com.mobicloud.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.RevokeFileBlocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val revokeFileBlocksUseCase: RevokeFileBlocksUseCase
) : ViewModel() {

    val deletedEntries: StateFlow<List<CatalogEntry>> =
        catalogRepository.getDeletedEntriesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // F8 fix: erreurs DB remontées à l'UI via un SharedFlow (message localisé)
    private val _errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    init {
        // F4 fix (AC-8): purger les entrées expirées à l'ouverture de TrashScreen aussi
        viewModelScope.launch { catalogRepository.purgeExpired() }
    }

    fun restoreEntry(fileHash: String) {
        viewModelScope.launch {
            // F8 fix: informer l'UI si la restauration échoue
            catalogRepository.restoreFromTrash(fileHash).onFailure {
                _errorEvent.emit("Impossible de restaurer ce fichier.")
            }
        }
    }

    fun permanentlyDelete(fileHash: String) {
        viewModelScope.launch {
            revokeFileBlocksUseCase(fileHash)
            catalogRepository.permanentlyDelete(fileHash).onFailure {
                _errorEvent.emit("Impossible de supprimer ce fichier.")
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val entries = catalogRepository.getDeletedEntriesFlow().first()
            entries.forEach { revokeFileBlocksUseCase(it.fileHash) }
            catalogRepository.emptyTrash()
        }
    }
}
