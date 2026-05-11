package com.mobicloud.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.mobicloud.data.local.dao.NodeSettingsDao
import com.mobicloud.data.local.entity.NodeSettingsEntity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import javax.inject.Inject

class NodeSettingsRepositoryImpl(
    private val dao: NodeSettingsDao,
    private val freeSpaceProvider: () -> Long,
    private val clusterIdProvider: () -> String
) : NodeSettingsRepository {

    @Inject constructor(
        dao: NodeSettingsDao,
        @ApplicationContext context: Context,
        wifiNetworkRepository: WifiNetworkRepository
    ) : this(
        dao = dao,
        freeSpaceProvider = {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        },
        // Pas de fallback UUID random : si SSID indisponible (permission
        // localisation refusee, hors WiFi, etc.) on retourne "" -- le clusterId
        // sera recalcule au prochain refreshClusterIdFromWifi() (declenche par
        // les changements reseau dans le service P2P).
        clusterIdProvider = {
            wifiNetworkRepository.getCurrentSsid()
                ?.let { ssid -> ssidToClusterId(ssid) }
                ?: ""
        }
    )

    companion object {
        /** Derives a deterministic UUID v4 from a WiFi SSID via SHA-256. */
        internal fun ssidToClusterId(ssid: String): String {
            val b = MessageDigest.getInstance("SHA-256").digest(ssid.toByteArray()).copyOf(16)
            b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()  // version 4
            b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()  // variant bits
            val hex = b.joinToString("") { "%02x".format(it) }
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        }
    }

    private val initMutex = Mutex()

    private fun defaultBytes(): Long {
        val freeBytes = freeSpaceProvider()
        val twoGb = 2L * 1024 * 1024 * 1024
        return minOf(twoGb, (freeBytes * 0.20).toLong())
    }

    // FIX SPLIT-CLUSTER : self-healing du clusterId contre le SSID courant.
    //
    // Sans ce self-healing, un clusterId persiste depuis une session WiFi
    // precedente (ex : WiFi domestique) survit en DB et est utilise tel quel
    // au boot suivant -- meme si le telephone est maintenant sur un autre
    // WiFi. Resultat IRL observe : deux telephones sur le MEME WiFi peuvent
    // chacun garder un clusterId different (hash de leur ancien SSID), le
    // garde WG1 les rejette mutuellement, chacun s'auto-elit super-pair dans
    // son propre cluster -> split-cluster confirme par WifiClusterGuardTest.
    //
    // Sémantique :
    //  - WiFi disponible (clusterIdProvider() != "") : ECRASE le clusterId
    //    persiste si different. Le SSID actuel fait foi.
    //  - SSID indisponible (clusterIdProvider() == "") : conserve la valeur
    //    persistee. Ne pas geler "" en DB. La 4G adopte le clusterId via
    //    updateClusterId() a la reception d'un COORDINATOR.
    override suspend fun getSettings(): NodeSettings = initMutex.withLock {
        val current = dao.getSettings()?.toDomain()
            ?: NodeSettings(allocatedStorageBytes = defaultBytes())
        val derivedFromWifi = clusterIdProvider()

        return@withLock when {
            // En WiFi avec SSID lisible : le clusterId persiste DOIT correspondre.
            derivedFromWifi.isNotEmpty() && current.clusterId != derivedFromWifi -> {
                val updated = current.copy(clusterId = derivedFromWifi)
                dao.upsert(updated.toEntity())
                updated
            }
            // Aligne (en WiFi) ou hors WiFi (SSID indisponible) : no-op.
            else -> current
        }
    }

    // FIX SPLIT-CLUSTER : retourne le clusterId du SSID LIVE, sans toucher la DB.
    // A utiliser par Bully et le garde WG1 : la decision de clustering repose
    // sur le WiFi actuel, jamais sur un clusterId stale en DB.
    override suspend fun getCurrentWifiClusterId(): String = clusterIdProvider()

    // Recalcule le clusterId depuis le SSID courant. Appele a chaque changement
    // reseau (cf MobicloudP2PService) pour rattraper les cas ou :
    //  - L'app a demarre avant la connexion WiFi (SSID indisponible au boot)
    //  - La permission ACCESS_FINE_LOCATION a ete accordee apres le 1er getSettings()
    //  - Le WiFi a change (autre cluster)
    override suspend fun refreshClusterIdFromWifi(): String = initMutex.withLock {
        val newId = clusterIdProvider()
        val existing = dao.getSettings()
        val currentId = existing?.clusterId ?: ""
        if (newId.isEmpty()) {
            // Pas de SSID lisible -- conserver l'existant tel quel.
            return@withLock currentId
        }
        if (newId == currentId) {
            return@withLock currentId
        }
        val updated = existing?.copy(clusterId = newId)
            ?: NodeSettingsEntity(id = 0, allocatedStorageBytes = defaultBytes(), clusterId = newId)
        dao.upsert(updated)
        newId
    }

    // AC3 — adopte le clusterId du super-pair élu ; no-op si blank (AC5 legacy compat).
    override suspend fun updateClusterId(id: String) {
        if (id.isBlank()) return
        initMutex.withLock {
            val existing = dao.getSettings()
            val updated = existing?.copy(clusterId = id)
                ?: NodeSettingsEntity(id = 0, allocatedStorageBytes = defaultBytes(), clusterId = id)
            dao.upsert(updated)
        }
    }

    // P8: validate bytes is positive ; lock with initMutex pour préserver clusterId atomiquement (Story 9.1).
    override suspend fun updateAllocatedStorage(bytes: Long) {
        require(bytes > 0) { "allocatedStorageBytes must be positive, got $bytes" }
        initMutex.withLock {
            val existing = dao.getSettings()
            val clusterId = existing?.clusterId ?: ""
            dao.upsert(NodeSettingsEntity(id = 0, allocatedStorageBytes = bytes, clusterId = clusterId))
        }
    }

    // P-A7 — pas d'effet de bord (dao.upsert) dans un Flow.map : le mapping est pur.
    // L'initialisation de la ligne par défaut est garantie par getSettings() qui est appelé au démarrage.
    override fun observeSettings(): Flow<NodeSettings> =
        dao.observeSettings().map { entity ->
            entity?.toDomain() ?: NodeSettings(allocatedStorageBytes = defaultBytes())
        }

    // P3: freeSpace calculation lives in data/, not in presentation
    override fun observeFreeSpaceBytes(): Flow<Long> = flow {
        emit(freeSpaceProvider())
    }
}

private fun NodeSettingsEntity.toDomain() = NodeSettings(
    allocatedStorageBytes = allocatedStorageBytes,
    clusterId = clusterId,
    id = id
)

private fun NodeSettings.toEntity() = NodeSettingsEntity(
    id = id,
    allocatedStorageBytes = allocatedStorageBytes,
    clusterId = clusterId
)
