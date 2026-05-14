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
import kotlinx.coroutines.flow.distinctUntilChanged
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
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val twoGb = 2L * 1024 * 1024 * 1024
        return minOf(twoGb, (freeBytes * 0.20).toLong())
    }

    override suspend fun getSettings(): NodeSettings = initMutex.withLock {
        dao.getSettings()?.toDomain() ?: NodeSettings(allocatedStorageBytes = defaultBytes())
    }

    override suspend fun getClusterIdOnce(): String = getSettings().clusterId

    override suspend fun updateClusterId(id: String) {
        if (id.isBlank()) return
        initMutex.withLock {
            val existing = dao.getSettings()
            val updated = existing?.copy(clusterId = id)
                ?: NodeSettingsEntity(id = 0, allocatedStorageBytes = defaultBytes(), clusterId = id)
            dao.upsert(updated)
        }
    }

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
            dao.upsert(
                (existing ?: NodeSettingsEntity(id = 0, allocatedStorageBytes = bytes, clusterId = ""))
                    .copy(allocatedStorageBytes = bytes)
            )
        }
    }

    // Story 13.1 — préservation des autres champs (allocatedStorageBytes, clusterId) lors du toggle.
    override suspend fun updateExpertMode(enabled: Boolean) {
        initMutex.withLock {
            val existing = dao.getSettings()
                ?: NodeSettingsEntity(id = 0, allocatedStorageBytes = defaultBytes())
            dao.upsert(existing.copy(isExpertModeEnabled = enabled))
        }
    }

    override fun observeSettings(): Flow<NodeSettings> =
        dao.observeSettings().map { entity ->
            entity?.toDomain() ?: NodeSettings(allocatedStorageBytes = defaultBytes())
        }

    override fun observeExpertMode(): Flow<Boolean> =
        dao.observeExpertMode()
            .map { it ?: false }
            .distinctUntilChanged()

    override fun observeFreeSpaceBytes(): Flow<Long> = flow {
        val stat = StatFs(Environment.getDataDirectory().path)
        emit(stat.availableBlocksLong * stat.blockSizeLong)
    }
}

private fun NodeSettingsEntity.toDomain() = NodeSettings(
    allocatedStorageBytes = allocatedStorageBytes,
    clusterId = clusterId,
    isExpertModeEnabled = isExpertModeEnabled,
    id = id
)
