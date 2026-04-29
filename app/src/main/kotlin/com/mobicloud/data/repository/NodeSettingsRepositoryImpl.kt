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

    // P7: double-check locking to prevent concurrent default-init races
    override suspend fun getSettings(): NodeSettings {
        dao.getSettings()?.let { return it.toDomain() }
        return initMutex.withLock {
            dao.getSettings()?.toDomain() ?: run {
                val default = NodeSettings(allocatedStorageBytes = defaultBytes())
                dao.upsert(default.toEntity())
                default
            }
        }
    }

    // P8: validate that bytes is a positive value before persisting
    override suspend fun updateAllocatedStorage(bytes: Long) {
        require(bytes > 0) { "allocatedStorageBytes must be positive, got $bytes" }
        dao.upsert(NodeSettingsEntity(id = 0, allocatedStorageBytes = bytes))
    }

    // P6: when no row exists, upsert the default so subsequent emissions are consistent
    override fun observeSettings(): Flow<NodeSettings> =
        dao.observeSettings().map { entity ->
            if (entity == null) {
                val default = NodeSettings(allocatedStorageBytes = defaultBytes())
                dao.upsert(default.toEntity())
                default
            } else {
                entity.toDomain()
            }
        }

    // P3: freeSpace calculation lives in data/, not in presentation
    override fun observeFreeSpaceBytes(): Flow<Long> = flow {
        emit(freeSpaceProvider())
    }
}

private fun NodeSettingsEntity.toDomain() = NodeSettings(
    allocatedStorageBytes = allocatedStorageBytes,
    id = id
)

private fun NodeSettings.toEntity() = NodeSettingsEntity(
    id = id,
    allocatedStorageBytes = allocatedStorageBytes
)
