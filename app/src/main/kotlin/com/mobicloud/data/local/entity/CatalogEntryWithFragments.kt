package com.mobicloud.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class CatalogEntryWithFragments(
    @Embedded val catalogEntry: CatalogEntryEntity,
    @Relation(
        parentColumn = "file_hash",
        entityColumn = "catalog_file_hash"
    )
    val fragmentLocations: List<FragmentLocationEntity>
)
