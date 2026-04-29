package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.NodeSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeSettingsDao {

    @Query("SELECT * FROM node_settings WHERE id = 0")
    fun observeSettings(): Flow<NodeSettingsEntity?>

    @Query("SELECT * FROM node_settings WHERE id = 0")
    suspend fun getSettings(): NodeSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NodeSettingsEntity)
}
