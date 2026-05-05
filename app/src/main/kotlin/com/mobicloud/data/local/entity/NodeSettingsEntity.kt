package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "node_settings")
data class NodeSettingsEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "allocated_storage_bytes") val allocatedStorageBytes: Long,
    @ColumnInfo(name = "cluster_id") val clusterId: String = ""
)
