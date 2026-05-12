package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.MemberSnapshotEntity

@Dao
interface MemberSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: MemberSnapshotEntity)

    @Query("SELECT * FROM member_snapshot WHERE cluster_id = :clusterId LIMIT 1")
    suspend fun get(clusterId: String): MemberSnapshotEntity?

    @Query("DELETE FROM member_snapshot WHERE cluster_id = :clusterId")
    suspend fun delete(clusterId: String)
}
