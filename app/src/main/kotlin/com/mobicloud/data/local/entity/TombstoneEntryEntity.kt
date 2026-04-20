package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobicloud.domain.models.TombstoneEntry

@Entity(tableName = "tombstone_entries")
data class TombstoneEntryEntity(
    @PrimaryKey @ColumnInfo(name = "block_id") val blockId: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long
)

fun TombstoneEntryEntity.toDomain() = TombstoneEntry(blockId = blockId, deletedAt = deletedAt)
fun TombstoneEntry.toEntity() = TombstoneEntryEntity(blockId = blockId, deletedAt = deletedAt)
