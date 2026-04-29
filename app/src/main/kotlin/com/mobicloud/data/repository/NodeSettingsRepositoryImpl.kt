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
import kotlinx.coroutines.flow.map
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

    private fun defaultBytes(): Long {
        val freeBytes = freeSpaceProvider()
        val twoGb = 2L * 1024 * 1024 * 1024
        return minOf(twoGb, (freeBytes * 0.20).toLong())
    }

    override suspend fun getSettings(): NodeSettings {
        return dao.getSettings()?.toDomain() ?: run {
            val default = NodeSettings(allocatedStorageBytes = defaultBytes())
            dao.upsert(default.toEntity())
            default
        }
    }

    override suspend fun updateAllocatedStorage(bytes: Long) {
        dao.upsert(NodeSettingsEntity(id = 0, allocatedStorageBytes = bytes))
    }

    override fun observeSettings(): Flow<NodeSettings> =
        dao.observeSettings().map { entity ->
            entity?.toDomain() ?: NodeSettings(allocatedStorageBytes = defaultBytes())
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
