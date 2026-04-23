package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.DhtEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DhtDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DhtEntryEntity)

    @Query("SELECT * FROM dht_entries WHERE block_id = :blockId LIMIT 1")
    suspend fun findByBlockId(blockId: String): DhtEntryEntity?

    // Story 7.3 — liste tous les hôtes distincts d'un blockId (utile pour calcul sous-réplication)
    @Query("SELECT DISTINCT node_id FROM dht_entries WHERE block_id = :blockId")
    suspend fun findNodeIdsByBlockId(blockId: String): List<String>

    @Query("SELECT * FROM dht_entries WHERE node_id = :nodeId")
    suspend fun findByNodeId(nodeId: String): List<DhtEntryEntity>

    @Query("DELETE FROM dht_entries WHERE node_id = :nodeId")
    suspend fun deleteByNodeId(nodeId: String)

    @Query("SELECT * FROM dht_entries ORDER BY timestamp DESC")
    fun observeAllEntries(): Flow<List<DhtEntryEntity>>
}
