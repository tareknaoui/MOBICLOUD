package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.TombstoneEntryEntity

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TombstoneEntryEntity)

    @Query("SELECT * FROM tombstone_entries WHERE block_id = :blockId LIMIT 1")
    suspend fun findByBlockId(blockId: String): TombstoneEntryEntity?

    @Query("DELETE FROM tombstone_entries WHERE deleted_at < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM tombstone_entries WHERE block_id = :blockId")
    suspend fun countByBlockId(blockId: String): Int
}
