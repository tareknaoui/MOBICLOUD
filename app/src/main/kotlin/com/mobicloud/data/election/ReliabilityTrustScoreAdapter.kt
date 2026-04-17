package com.mobicloud.data.election

import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptateur qui expose le score de fiabilité local comme ITrustScoreProvider.
 * Retourne reliabilityScore * 100 comme Int (0-100).
 */
@Singleton
class ReliabilityTrustScoreAdapter @Inject constructor(
    private val securityRepository: SecurityRepository
) : ITrustScoreProvider {

    override suspend fun getTrustScore(nodeId: String): Int {
        val identity = securityRepository.getIdentity().getOrNull()
        val score = identity?.reliabilityScore ?: 0.5f
        return (score * 100).toInt()
    }
}
