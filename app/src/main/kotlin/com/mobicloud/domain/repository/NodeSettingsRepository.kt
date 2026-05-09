package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeSettings
import kotlinx.coroutines.flow.Flow

interface NodeSettingsRepository {
    suspend fun getSettings(): NodeSettings
    suspend fun updateAllocatedStorage(bytes: Long)
    suspend fun updateClusterId(id: String)

    /**
     * Recalcule le clusterId depuis le SSID WiFi courant si possible.
     * Sans permission de localisation ou hors WiFi, le SSID est null --
     * dans ce cas le clusterId reste vide (pas de fallback UUID random
     * qui figerait un cluster aleatoire a vie). A appeler quand le reseau
     * change ou apres acquisition de la permission.
     *
     * @return le clusterId resultant (potentiellement "" si SSID indisponible)
     */
    suspend fun refreshClusterIdFromWifi(): String

    fun observeSettings(): Flow<NodeSettings>
    fun observeFreeSpaceBytes(): Flow<Long>
}
