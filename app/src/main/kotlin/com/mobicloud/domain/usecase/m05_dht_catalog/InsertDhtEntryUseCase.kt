package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.repository.DhtRepository
import javax.inject.Inject

class InsertDhtEntryUseCase @Inject constructor(
    private val dhtRepository: DhtRepository
) {

    suspend operator fun invoke(
        blockId: String,
        nodeId: String,
        ipAddress: String,
        port: Int
    ): Result<Unit> {
        return dhtRepository.insertEntry(blockId, nodeId, ipAddress, port)
    }
}
