package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.repository.DhtRepository
import javax.inject.Inject

class LookupBlockLocationUseCase @Inject constructor(
    private val dhtRepository: DhtRepository
) {

    suspend operator fun invoke(blockId: String): Result<DhtEntry?> {
        return dhtRepository.findByBlockId(blockId)
    }
}
