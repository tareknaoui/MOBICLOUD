package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosted_blocks")
data class HostedBlockEntity(
    @PrimaryKey @ColumnInfo(name = "block_id") val blockId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "fragment_index") val fragmentIndex: Int,
    @ColumnInfo(name = "is_parity") val isParity: Boolean,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long = System.currentTimeMillis()
)
