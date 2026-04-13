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
}
