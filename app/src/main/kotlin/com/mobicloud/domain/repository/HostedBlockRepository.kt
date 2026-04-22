package com.mobicloud.domain.repository

interface HostedBlockRepository {
    suspend fun saveBlock(
        blockId: String,
        ownerId: String,
        fragmentIndex: Int,
        isParity: Boolean,
        ciphertext: ByteArray
    ): Result<String>

    suspend fun blockExists(blockId: String): Boolean

    suspend fun deleteBlock(blockId: String)
}
