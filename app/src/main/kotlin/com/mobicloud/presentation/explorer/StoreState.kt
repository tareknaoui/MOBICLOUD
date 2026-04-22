package com.mobicloud.presentation.explorer

import com.mobicloud.domain.models.CatalogEntry

sealed class StoreState {
    object Idle : StoreState()
    sealed class InProgress : StoreState() {
        object Encoding : InProgress()
        object Encrypting : InProgress()
        data class Distributing(
            val confirmedIndices: Set<Int> = emptySet(),
            val total: Int,
            val dataBlockCount: Int,
            val failedIndices: Set<Int> = emptySet()
        ) : InProgress() {
            val confirmed: Int get() = confirmedIndices.size
        }
    }
    data class Success(val entry: CatalogEntry, val nodeCount: Int) : StoreState()
    data class Error(val message: String) : StoreState()
}
