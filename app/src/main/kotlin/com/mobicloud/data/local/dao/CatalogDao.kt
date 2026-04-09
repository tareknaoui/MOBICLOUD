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
    suspend fun insertCatalogEntry(entry: CatalogEntryEntity)

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

    @Transaction
    suspend fun insertWithFragments(entry: CatalogEntryEntity, fragments: List<FragmentLocationEntity>) {
        insertCatalogEntry(entry)
        deleteFragmentLocations(entry.fileHash)
        insertFragmentLocations(fragments)
    }
}
