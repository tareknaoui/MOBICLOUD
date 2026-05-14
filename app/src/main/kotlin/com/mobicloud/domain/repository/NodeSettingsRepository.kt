package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeSettings
import kotlinx.coroutines.flow.Flow

interface NodeSettingsRepository {
    suspend fun getSettings(): NodeSettings
    suspend fun updateAllocatedStorage(bytes: Long)
    suspend fun updateClusterId(id: String)

    // P14 review (Story 12.1) : reset explicite du sticky cluster sur rejet définitif
    // (CLUSTER_FULL / INVALID_STATE) — `updateClusterId("")` est no-op par design.
    suspend fun clearClusterId()

    // Story 12.1 — retourne le clusterId persiste (attribue par JOIN_ACCEPT ou BullySolo).
    // Le clusterId n'est plus jamais derive du SSID WiFi.
    suspend fun getClusterIdOnce(): String

    fun observeSettings(): Flow<NodeSettings>
    fun observeFreeSpaceBytes(): Flow<Long>

    // Story 13.1 — Mode Diagnostics Avancés (toggle UI Simple/Expert)
    suspend fun updateExpertMode(enabled: Boolean)
    fun observeExpertMode(): Flow<Boolean>
}
