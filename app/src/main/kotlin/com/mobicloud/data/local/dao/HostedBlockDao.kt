package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.HostedBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostedBlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHostedBlock(block: HostedBlockEntity)

    @Query("SELECT * FROM hosted_blocks WHERE block_id = :blockId")
    suspend fun getHostedBlock(blockId: String): HostedBlockEntity?

    @Query("SELECT * FROM hosted_blocks")
    fun getAllHostedBlocksFlow(): Flow<List<HostedBlockEntity>>

    @Query("DELETE FROM hosted_blocks WHERE block_id = :blockId")
    suspend fun deleteHostedBlock(blockId: String)

    // Fix P9 : ORDER BY garantit un ordre stable pour la signature du payload DEPARTURE_NOTICE
    @Query("SELECT block_id FROM hosted_blocks ORDER BY block_id ASC")
    suspend fun getAllBlockIds(): List<String>
}
