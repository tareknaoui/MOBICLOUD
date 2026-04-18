package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mobicloud.domain.models.DhtEntry

@Entity(
    tableName = "dht_entries",
    indices = [
        Index("block_id"),
        Index("node_id")
    ]
)
data class DhtEntryEntity(
    @PrimaryKey @ColumnInfo(name = "block_id") val blockId: String,
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "ip_address") val ipAddress: String,
    val port: Int,
    val timestamp: Long
)

fun DhtEntryEntity.toDomain() = DhtEntry(
    blockId = blockId,
    nodeId = nodeId,
    ipAddress = ipAddress,
    port = port,
    timestamp = timestamp
)

fun DhtEntry.toEntity() = DhtEntryEntity(
    blockId = blockId,
    nodeId = nodeId,
    ipAddress = ipAddress,
    port = port,
    timestamp = timestamp
)
