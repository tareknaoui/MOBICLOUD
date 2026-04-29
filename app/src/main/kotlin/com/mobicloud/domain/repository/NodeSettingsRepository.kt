package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeSettings
import kotlinx.coroutines.flow.Flow

interface NodeSettingsRepository {
    suspend fun getSettings(): NodeSettings
    suspend fun updateAllocatedStorage(bytes: Long)
    fun observeSettings(): Flow<NodeSettings>
    fun observeFreeSpaceBytes(): Flow<Long>
}
