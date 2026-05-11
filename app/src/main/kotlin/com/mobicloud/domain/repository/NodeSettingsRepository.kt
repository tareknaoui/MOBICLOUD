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

    /**
     * Retourne le clusterId derive UNIQUEMENT du SSID WiFi courant, sans
     * consultation de la DB. Si le SSID est indisponible (4G, permission
     * location refusee, hotspot AP), retourne "".
     *
     * FIX SPLIT-CLUSTER : a utiliser dans Bully (broadcast COORDINATOR) et
     * dans le garde WG1 (rejet cross-cluster) pour garantir que la decision
     * de clustering repose toujours sur l'etat WiFi LIVE, jamais sur un
     * clusterId stale en DB d'une session precedente.
     */
    suspend fun getCurrentWifiClusterId(): String

    fun observeSettings(): Flow<NodeSettings>
    fun observeFreeSpaceBytes(): Flow<Long>
}
