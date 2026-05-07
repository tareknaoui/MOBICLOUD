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
    val versionClock: Long,

    @ColumnInfo(name = "wrapped_master_key_json")
    val wrappedMasterKeyJson: String? = null,

    // Story 6.3 — taille originale du fichier (octets) pour permettre au décodeur Erasure
    // de trimer le padding lors du réassemblage. 0L pour les entrées pré-6.3 (le pipeline
    // de téléchargement lèvera DownloadException.MasterKeyTransportGap).
    @ColumnInfo(name = "original_file_size", defaultValue = "0")
    val originalFileSize: Long = 0L,

    @ColumnInfo(name = "original_file_name", defaultValue = "")
    val originalFileName: String = "",

    @ColumnInfo(name = "k", defaultValue = "4")
    val k: Int = 4,

    @ColumnInfo(name = "n", defaultValue = "2")
    val n: Int = 2
)
