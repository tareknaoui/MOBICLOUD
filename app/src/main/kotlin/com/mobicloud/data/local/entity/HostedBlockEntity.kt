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
    @ColumnInfo(name = "iv", typeAffinity = ColumnInfo.BLOB) val iv: ByteArray,
    @ColumnInfo(name = "received_at") val receivedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HostedBlockEntity) return false
        return blockId == other.blockId &&
                ownerId == other.ownerId &&
                fragmentIndex == other.fragmentIndex &&
                isParity == other.isParity &&
                filePath == other.filePath &&
                sizeBytes == other.sizeBytes &&
                iv.contentEquals(other.iv) &&
                receivedAt == other.receivedAt
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + isParity.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + receivedAt.hashCode()
        return result
    }
}
