package com.mobicloud.domain.repository

import com.mobicloud.domain.models.CatalogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Contrat Clean Architecture pour l'acc\u00e8s au catalogue distribu\u00e9 Zero-Knowledge.
 *
 * R\u00e8gles :
 * - Toutes les op\u00e9rations d'\u00e9criture/lecture sync retournent [Result]<T> et sont ex\u00e9cut\u00e9es sur Dispatchers.IO.
 * - [insertEntry] applique le filtre DHT : si le `fileHash` de l'entr\u00e9e n'appartient pas \u00e0 la
 *   partition `[nodeId, successorId)`, l'insertion est silencieusement ignor\u00e9e (AC #6).
 * - [insertEntry] remplace int\u00e9gralement les fragments existants (full replace atomique).
 *   Pour mettre \u00e0 jour l'entr\u00e9e seule, utiliser [updateEntryOnly].
 */
interface CatalogRepository {

    /**
     * Ins\u00e8re une entr\u00e9e dans le catalogue local si le [CatalogEntry.fileHash] appartient
     * \u00e0 la partition DHT `[nodeId, successorId)`. Sinon, l'op\u00e9ration est silencieusement ignor\u00e9e.
     *
     * @param entry La fiche catalogue \u00e0 ins\u00e9rer.
     * @param nodeId L'ID de ce n\u0153ud sur l'anneau DHT.
     * @param successorId L'ID du n\u0153ud successeur sur l'anneau DHT.
     * @return [Result.success] si l'entr\u00e9e est ins\u00e9r\u00e9e ou silencieusement ignor\u00e9e, [Result.failure] en cas d'erreur de persistance.
     */
    suspend fun insertEntry(
        entry: CatalogEntry,
        nodeId: String,
        successorId: String
    ): Result<Unit>

    /**
     * Met \u00e0 jour les m\u00e9tadonn\u00e9es d'une entr\u00e9e sans toucher \u00e0 ses fragments. (D2-B)
     */
    suspend fun updateEntryOnly(entry: CatalogEntry): Result<Unit>

    /**
     * Observe en continu une fiche du catalogue par son [fileHash]. \u00c9met `null` si absente.
     */
    fun getEntryFlow(hash: String): Flow<CatalogEntry?>

    /**
     * Observe en continu l'int\u00e9gralit\u00e9 du catalogue local.
     */
    fun getAllEntriesFlow(): Flow<List<CatalogEntry>>

    /**
     * Lecture ponctuelle d'une fiche par son [fileHash].
     */
    suspend fun getEntry(hash: String): Result<CatalogEntry?>
}
