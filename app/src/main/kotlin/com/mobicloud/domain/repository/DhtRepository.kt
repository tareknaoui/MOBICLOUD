package com.mobicloud.domain.repository

import com.mobicloud.domain.models.DhtEntry
import kotlinx.coroutines.flow.Flow

interface DhtRepository {
    suspend fun insertEntry(blockId: String, nodeId: String, ipAddress: String, port: Int): Result<Unit>

    suspend fun insertEntryWithTimestamp(
        blockId: String,
        nodeId: String,
        ipAddress: String,
        port: Int,
        timestamp: Long
    ): Result<Unit>

    suspend fun findByBlockId(blockId: String): Result<DhtEntry?>

    suspend fun findByNodeId(nodeId: String): Result<List<DhtEntry>>

    suspend fun deleteByNodeId(nodeId: String): Result<Unit>

    fun observeAllEntries(): Flow<List<DhtEntry>>
}
