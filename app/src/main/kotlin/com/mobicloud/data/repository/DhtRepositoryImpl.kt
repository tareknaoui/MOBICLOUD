package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.DhtDao
import com.mobicloud.data.local.entity.DhtEntryEntity
import com.mobicloud.data.local.entity.toDomain
import com.mobicloud.data.local.entity.toEntity
import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.repository.DhtRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DhtRepositoryImpl @Inject constructor(
    private val dhtDao: DhtDao
) : DhtRepository {

    override suspend fun insertEntry(blockId: String, nodeId: String, ipAddress: String, port: Int): Result<Unit> =
        runCatching {
            val timestamp = System.currentTimeMillis()
            val entry = DhtEntryEntity(
                blockId = blockId,
                nodeId = nodeId,
                ipAddress = ipAddress,
                port = port,
                timestamp = timestamp
            )
            dhtDao.insert(entry)
        }

    override suspend fun insertEntryWithTimestamp(
        blockId: String,
        nodeId: String,
        ipAddress: String,
        port: Int,
        timestamp: Long
    ): Result<Unit> = runCatching {
        dhtDao.insert(DhtEntryEntity(
            blockId = blockId,
            nodeId = nodeId,
            ipAddress = ipAddress,
            port = port,
            timestamp = timestamp
        ))
    }

    override suspend fun findByBlockId(blockId: String): Result<DhtEntry?> =
        runCatching {
            dhtDao.findByBlockId(blockId)?.toDomain()
        }

    override suspend fun findByNodeId(nodeId: String): Result<List<DhtEntry>> =
        runCatching {
            dhtDao.findByNodeId(nodeId).map { it.toDomain() }
        }

    override suspend fun deleteByNodeId(nodeId: String): Result<Unit> =
        runCatching {
            dhtDao.deleteByNodeId(nodeId)
        }

    override fun observeAllEntries(): Flow<List<DhtEntry>> =
        dhtDao.observeAllEntries()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
}
