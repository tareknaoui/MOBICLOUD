package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.NodeIdentityEntity

@Dao
interface IdentityDao {
    @Query("SELECT * FROM node_identity LIMIT 1")
    suspend fun getIdentity(): NodeIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentity(identity: NodeIdentityEntity)
    
    @Query("DELETE FROM node_identity")
    suspend fun clearIdentity()
}
