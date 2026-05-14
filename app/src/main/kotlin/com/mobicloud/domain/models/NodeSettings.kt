package com.mobicloud.domain.models

data class NodeSettings(
    val allocatedStorageBytes: Long,
    val clusterId: String = "",
    val isExpertModeEnabled: Boolean = false,
    val id: Int = 0
)
