package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.CatalogEntryWithFragments
import com.mobicloud.data.local.entity.FragmentLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFragmentLocations(fragments: List<FragmentLocationEntity>)

    @Transaction
    @Query("SELECT * FROM catalog_entry WHERE file_hash = :hash")
    fun getCatalogEntryFlow(hash: String): Flow<CatalogEntryWithFragments?>

    @Transaction
    @Query("SELECT * FROM catalog_entry")
    fun getAllCatalogEntriesFlow(): Flow<List<CatalogEntryWithFragments>>

    @Transaction
    @Query("SELECT * FROM catalog_entry WHERE file_hash = :hash")
    suspend fun getCatalogEntry(hash: String): CatalogEntryWithFragments?

    @Query("DELETE FROM fragment_location WHERE catalog_file_hash = :hash")
    suspend fun deleteFragmentLocations(hash: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogEntryInternal(entry: CatalogEntryEntity)

    /**
     * Insère une entrée catalogue et REMPLACE intégralement ses fragments (full replace atomique).
     * ATTENTION : passer `fragments = emptyList()` supprime tous les fragments existants.
     * Pour une mise à jour de l'entrée seule, utiliser [updateCatalogEntryOnly].
     */
    @Transaction
    suspend fun insertWithFragments(entry: CatalogEntryEntity, fragments: List<FragmentLocationEntity>) {
        // Ordre critique (BH-01) : delete AVANT insert pour éviter tout état incohérent
        // observable par les Flow pendant la fenêtsre de transaction.
        deleteFragmentLocations(entry.fileHash)
        insertCatalogEntryInternal(entry)
        insertFragmentLocations(fragments)
    }

    /**
     * Met à jour l'entrée catalogue sans modifier les fragments associés. (D2-B)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCatalogEntryOnly(entry: CatalogEntryEntity)

    // Story 13.2 — requêtes corbeille (soft-delete local, non distribué dans le DHT)

    @Transaction
    @Query("SELECT * FROM catalog_entry WHERE is_in_trash = 0")
    fun getAllActiveEntriesFlow(): Flow<List<CatalogEntryWithFragments>>

    @Transaction
    @Query("SELECT * FROM catalog_entry WHERE is_in_trash = 1")
    fun getDeletedEntriesFlow(): Flow<List<CatalogEntryWithFragments>>

    @Query("UPDATE catalog_entry SET is_in_trash = 1, deleted_at = :ts WHERE file_hash = :hash")
    suspend fun softDeleteEntry(hash: String, ts: Long)

    @Query("UPDATE catalog_entry SET is_in_trash = 0, deleted_at = NULL WHERE file_hash = :hash")
    suspend fun restoreEntry(hash: String)

    @Query("DELETE FROM catalog_entry WHERE file_hash = :hash")
    suspend fun deleteCatalogEntry(hash: String)

    @Transaction
    suspend fun permanentlyDeleteEntry(hash: String) {
        deleteFragmentLocations(hash)
        deleteCatalogEntry(hash)
    }

    @Transaction
    @Query("SELECT * FROM catalog_entry")
    suspend fun getAllWithFragmentsOnce(): List<CatalogEntryWithFragments>

    @Transaction
    @Query("SELECT * FROM catalog_entry WHERE is_in_trash = 1 AND deleted_at < :expiryTs")
    suspend fun getExpiredEntries(expiryTs: Long): List<CatalogEntryWithFragments>

    @Query("SELECT file_hash FROM catalog_entry WHERE is_in_trash = 1")
    suspend fun getAllTrashHashes(): List<String>

    @Transaction
    suspend fun purgeExpiredEntries(expiryTs: Long) {
        val expired = getExpiredEntries(expiryTs)
        for (e in expired) {
            permanentlyDeleteEntry(e.catalogEntry.fileHash)
        }
    }

    @Transaction
    suspend fun emptyTrash() {
        val hashes = getAllTrashHashes()
        for (h in hashes) {
            permanentlyDeleteEntry(h)
        }
    }

    // Story 13.5 — organisation en dossiers (local uniquement, non distribué dans le DHT)

    @Query("UPDATE catalog_entry SET folder_path = :folder WHERE file_hash = :hash")
    suspend fun updateFolderPath(hash: String, folder: String?)

    @Query("SELECT DISTINCT folder_path FROM catalog_entry WHERE is_in_trash = 0 AND folder_path IS NOT NULL ORDER BY folder_path ASC")
    fun getActiveFolderNamesFlow(): Flow<List<String>>

    @Query("UPDATE catalog_entry SET folder_path = :newPath WHERE folder_path = :oldPath")
    suspend fun renameFolder(oldPath: String, newPath: String)

    @Query("UPDATE catalog_entry SET folder_path = NULL WHERE folder_path = :folderPath")
    suspend fun moveFilesToRoot(folderPath: String)
}
