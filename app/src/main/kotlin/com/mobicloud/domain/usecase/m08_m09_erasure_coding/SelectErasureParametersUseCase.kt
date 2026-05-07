package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Choisit les paramètres Erasure Coding selon le score de fiabilité du réseau au moment de l'upload.
 *
 * Le score réseau est la moyenne du score local et des SF de tous les pairs actifs :
 *   networkScore = (localScore + Σ peer.reliabilityScore) / (1 + activePeerCount)
 * Sans pairs actifs, seul le score local est utilisé.
 *
 * Score ≥ 0.7  → K=4 N=2 — réseau fiable, overhead minimal
 * Score ≥ 0.4  → K=3 N=3 — fiabilité moyenne, plus de copies de secours
 * Score < 0.4  → K=2 N=4 — réseau peu fiable, redondance maximale
 *
 * Contrainte GF(256) : K+N ≤ 255. Les trois profils ci-dessus la respectent.
 */
@Singleton
class SelectErasureParametersUseCase @Inject constructor(
    private val trustScoreProvider: ITrustScoreProvider,
    private val peerRepository: PeerRepository
) {
    suspend operator fun invoke(): ErasureParameters {
        val localScore = trustScoreProvider.getScore()
        val activePeers = peerRepository.peers.value.filter { it.isActive }
        val networkScore = if (activePeers.isEmpty()) {
            localScore
        } else {
            val peerSum = activePeers.sumOf { it.identity.reliabilityScore.toDouble() }.toFloat()
            (localScore + peerSum) / (1 + activePeers.size)
        }
        return when {
            networkScore >= 0.7f -> ErasureParameters(k = 4, n = 2)
            networkScore >= 0.4f -> ErasureParameters(k = 3, n = 3)
            else                 -> ErasureParameters(k = 2, n = 4)
        }
    }
}
