package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.TombstoneDao
import com.mobicloud.data.local.entity.toDomain
import com.mobicloud.data.local.entity.toEntity
import com.mobicloud.domain.models.TombstoneEntry
import com.mobicloud.domain.repository.TombstoneRepository
import javax.inject.Inject

class TombstoneRepositoryImpl @Inject constructor(
    private val tombstoneDao: TombstoneDao
) : TombstoneRepository {

    override suspend fun insert(tombstone: TombstoneEntry): Result<Unit> =
        runCatching { tombstoneDao.insert(tombstone.toEntity()) }

    override suspend fun findByBlockId(blockId: String): Result<TombstoneEntry?> =
        runCatching { tombstoneDao.findByBlockId(blockId)?.toDomain() }

    override suspend fun deleteOlderThan(cutoffTimestamp: Long): Result<Int> =
        runCatching { tombstoneDao.deleteOlderThan(cutoffTimestamp) }

    override suspend fun existsForBlock(blockId: String): Boolean =
        runCatching { tombstoneDao.countByBlockId(blockId) > 0 }.getOrDefault(false)
}
