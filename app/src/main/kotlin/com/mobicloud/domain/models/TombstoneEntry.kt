package com.mobicloud.domain.models

data class TombstoneEntry(
    val blockId: String,
    val deletedAt: Long
)
