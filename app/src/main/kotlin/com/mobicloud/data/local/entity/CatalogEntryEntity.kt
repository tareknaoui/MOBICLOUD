package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_entry")
data class CatalogEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_hash")
    val fileHash: String,

    @ColumnInfo(name = "owner_pub_key_hash")
    val ownerPubKeyHash: String,

    @ColumnInfo(name = "version_clock")
    val versionClock: Long
)
