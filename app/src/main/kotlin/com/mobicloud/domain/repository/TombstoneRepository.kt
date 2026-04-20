package com.mobicloud.domain.repository

import com.mobicloud.domain.models.TombstoneEntry

interface TombstoneRepository {
    suspend fun insert(tombstone: TombstoneEntry): Result<Unit>
    suspend fun findByBlockId(blockId: String): Result<TombstoneEntry?>
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Result<Int>
    suspend fun existsForBlock(blockId: String): Boolean
}
