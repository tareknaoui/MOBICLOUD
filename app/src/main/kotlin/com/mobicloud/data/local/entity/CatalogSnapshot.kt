package com.mobicloud.data.local.entity

import kotlinx.serialization.Serializable

@Serializable
data class FragmentLocationSnapshot(
    val fragmentIndex: Int,
    val fragmentHash: String,
    val nodeIds: String
)

@Serializable
data class CatalogEntrySnapshot(
    val fileHash: String,
    val ownerPubKeyHash: String,
    val versionClock: Long,
    val wrappedMasterKeyJson: String? = null,
    val originalFileSize: Long = 0L,
    val originalFileName: String = "",
    val k: Int = 4,
    val n: Int = 2,
    val isInTrash: Boolean = false,
    val deletedAt: Long? = null,
    val folderPath: String? = null,
    val fragments: List<FragmentLocationSnapshot> = emptyList()
)

@Serializable
data class CatalogSnapshot(
    val version: Int = 1,
    val entries: List<CatalogEntrySnapshot> = emptyList()
)
