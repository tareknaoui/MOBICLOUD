package com.mobicloud.domain.usecase.m01_discovery

import javax.inject.Inject

class CalculateReliabilityScoreUseCase @Inject constructor(
    private val provider: ITrustScoreProvider
) {
    suspend operator fun invoke(): Result<Float> = runCatching {
        provider.getScore().coerceIn(0.0f, 1.0f)
    }
}
