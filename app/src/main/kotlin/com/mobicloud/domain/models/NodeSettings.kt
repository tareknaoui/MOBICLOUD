package com.mobicloud.domain.models

data class NodeSettings(
    val allocatedStorageBytes: Long,
    val clusterId: String = "",
    val id: Int = 0
)
