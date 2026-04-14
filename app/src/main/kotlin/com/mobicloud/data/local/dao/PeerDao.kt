package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mobicloud.data.local.entity.PeerNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Upsert
    suspend fun insertOrUpdate(peer: PeerNodeEntity)

    @Query("UPDATE peer_nodes SET is_active = 0 WHERE last_seen_timestamp_ms < :cutoffMs")
    suspend fun markInactive(cutoffMs: Long)

    @Query("SELECT * FROM peer_nodes")
    fun getAllPeers(): Flow<List<PeerNodeEntity>>
}
