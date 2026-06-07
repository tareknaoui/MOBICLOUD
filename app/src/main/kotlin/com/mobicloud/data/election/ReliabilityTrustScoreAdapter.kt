package com.mobicloud.data.election

import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.IdentityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptateur qui expose le score de fiabilité local comme ITrustScoreProvider.
 * Retourne reliabilityScore * 100 comme Int (0-100).
 * Utilise IdentityRepository (DB) pour avoir le score mis à jour par calculateReliabilityScoreUseCase.
 */
@Singleton
class ReliabilityTrustScoreAdapter @Inject constructor(
    private val identityRepository: IdentityRepository
) : ITrustScoreProvider {

    override suspend fun getTrustScore(nodeId: String): Int {
        val score = identityRepository.getIdentity().getOrNull()?.reliabilityScore ?: 0.5f
        return (score * 100).toInt()
    }
}
