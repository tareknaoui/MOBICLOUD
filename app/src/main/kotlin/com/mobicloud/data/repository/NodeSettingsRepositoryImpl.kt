package com.mobicloud.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.mobicloud.data.local.dao.NodeSettingsDao
import com.mobicloud.data.local.entity.NodeSettingsEntity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.repository.NodeSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class NodeSettingsRepositoryImpl @Inject constructor(
    private val dao: NodeSettingsDao,
    @ApplicationContext private val context: Context
) : NodeSettingsRepository {

    private val initMutex = Mutex()

    private fun defaultBytes(): Long {
        // P16 review : factoriser le StatFs (avant : 2 syscalls + risque d'incohérence entre les deux lectures).
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val twoGb = 2L * 1024 * 1024 * 1024
        return minOf(twoGb, (freeBytes * 0.20).toLong())
    }

    override suspend fun getSettings(): NodeSettings = initMutex.withLock {
        dao.getSettings()?.toDomain() ?: NodeSettings(allocatedStorageBytes = defaultBytes())
    }

    // Story 12.1 — retourne le clusterId persiste directement, sans derivation SSID.
    override suspend fun getClusterIdOnce(): String = getSettings().clusterId

    // AC6 — adopte le clusterId du super-pair élu ; no-op si blank.
    override suspend fun updateClusterId(id: String) {
        if (id.isBlank()) return
        initMutex.withLock {
            val existing = dao.getSettings()
            val updated = existing?.copy(clusterId = id)
                ?: NodeSettingsEntity(id = 0, allocatedStorageBytes = defaultBytes(), clusterId = id)
            dao.upsert(updated)
        }
    }

    // P14 review — reset explicite (utilisé sur rejet définitif d'un cluster sticky).
    override suspend fun clearClusterId() {
        initMutex.withLock {
            val existing = dao.getSettings() ?: return@withLock
            dao.upsert(existing.copy(clusterId = ""))
        }
    }

    override suspend fun updateAllocatedStorage(bytes: Long) {
        require(bytes > 0) { "allocatedStorageBytes must be positive, got $bytes" }
        initMutex.withLock {
            val existing = dao.getSettings()
            val clusterId = existing?.clusterId ?: ""
            dao.upsert(NodeSettingsEntity(id = 0, allocatedStorageBytes = bytes, clusterId = clusterId))
        }
    }

    override fun observeSettings(): Flow<NodeSettings> =
        dao.observeSettings().map { entity ->
            entity?.toDomain() ?: NodeSettings(allocatedStorageBytes = defaultBytes())
        }

    override fun observeFreeSpaceBytes(): Flow<Long> = flow {
        val stat = StatFs(Environment.getDataDirectory().path)
        emit(stat.availableBlocksLong * stat.blockSizeLong)
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
