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

class NodeSettingsRepositoryImpl(
    private val dao: NodeSettingsDao,
    private val freeSpaceProvider: () -> Long
) : NodeSettingsRepository {

    @Inject constructor(
        dao: NodeSettingsDao,
        @ApplicationContext context: Context
    ) : this(dao, {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.availableBlocksLong * stat.blockSizeLong
    })

    private val initMutex = Mutex()

    private fun defaultBytes(): Long {
        val freeBytes = freeSpaceProvider()
        val twoGb = 2L * 1024 * 1024 * 1024
        return minOf(twoGb, (freeBytes * 0.20).toLong())
    }

    // P7: double-check locking + lazy clusterId backfill (Story 9.1).
    override suspend fun getSettings(): NodeSettings {
        dao.getSettings()?.toDomain()?.let { existing ->
            if (existing.clusterId.isNotEmpty()) return existing
        }
        return initMutex.withLock {
            val current = dao.getSettings()?.toDomain()
                ?: NodeSettings(allocatedStorageBytes = defaultBytes())
            if (current.clusterId.isNotEmpty()) {
                current
            } else {
                val withCluster = current.copy(clusterId = java.util.UUID.randomUUID().toString())
                dao.upsert(withCluster.toEntity())
                withCluster
            }
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
