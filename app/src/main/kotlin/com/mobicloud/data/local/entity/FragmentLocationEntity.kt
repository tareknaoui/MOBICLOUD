package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fragment_location",
    foreignKeys = [
        ForeignKey(
            entity = CatalogEntryEntity::class,
            parentColumns = ["file_hash"],
            childColumns = ["catalog_file_hash"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("catalog_file_hash")
    ]
)
data class FragmentLocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "catalog_file_hash")
    val catalogFileHash: String,

    @ColumnInfo(name = "fragment_index")
    val fragmentIndex: Int,

    @ColumnInfo(name = "fragment_hash")
    val fragmentHash: String,

    @ColumnInfo(name = "node_ids")
    val nodeIds: String // List<String> inside JSON
)
