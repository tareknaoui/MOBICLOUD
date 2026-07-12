package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.ClusterInvite
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject

/**
 * Génère une invitation pour amener un ami dans le cluster local (lien / QR).
 * Nécessite d'être déjà membre d'un cluster — pas de génération avant la première jointure.
 */
class GenerateClusterInviteUseCase @Inject constructor(
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val peerRepository: PeerRepository,
    private val securityRepository: SecurityRepository
) {
    class NotInClusterException : Exception("Pas encore dans un cluster — impossible de générer une invitation")

    suspend operator fun invoke(): Result<ClusterInvite> = runCatching {
        val clusterId = nodeSettingsRepository.getClusterIdOnce()
        if (clusterId.isBlank()) throw NotInClusterException()

        val identity = securityRepository.getIdentity().getOrThrow()
        // Hint d'affichage uniquement : le Super-Pair courant si connu, sinon self (couvre le
        // cas où le nœud local EST le Super-Pair). Jamais utilisé comme cible dure au join.
        val hintedSp = peerRepository.peers.value
            .firstOrNull { it.isSuperPair }
            ?.identity?.nodeId
            ?: identity.nodeId

        ClusterInvite(clusterId = clusterId, hintedSpNodeId = hintedSp)
    }
}
