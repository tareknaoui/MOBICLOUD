package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.electionSignedBytes
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject

class AbdicateSuperPeerUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val trustScoreProvider: ITrustScoreProvider,
    private val networkClient: IElectionNetworkClient,
    private val electionStateManager: ElectionStateManager
) {
    /**
     * @param rotation true pour une fin de mandat planifiée : retrait souple
     * ([MandateRotationPolicy]) au lieu du cooldown strict de 5 min, qui empêchait
     * l'ancien Super-Pair de reprendre le rôle même s'il restait seul.
     */
    suspend operator fun invoke(rotation: Boolean = false): Result<Unit> {
        val identity = securityRepository.getIdentity().getOrElse {
            return Result.failure(it)
        }
        val score = trustScoreProvider.getTrustScore(identity.nodeId).toFloat()
        val timestampMs = System.currentTimeMillis()

        val dataToSign = electionSignedBytes(
            type = ElectionMessageType.ABDICATION,
            senderNodeId = identity.nodeId,
            reliabilityScore = score,
            clusterId = "",
            timestampMs = timestampMs
        )
        val signature = securityRepository.signData(dataToSign).getOrElse {
            return Result.failure(Exception("Failed to sign ABDICATION payload", it))
        }

        val payload = ElectionPayload(
            senderNodeId = identity.nodeId,
            type = ElectionMessageType.ABDICATION,
            reliabilityScore = score,
            signatureBytes = signature,
            timestampMs = timestampMs
        )

        // Fin du mandat local, que la diffusion réussisse ou non : le nœud ne doit plus
        // réaffirmer un term de Super-Pair qu'il n'exerce plus.
        electionStateManager.endMandate()

        // 1. Diffuser l'abdication en premier
        val broadcastResult = networkClient.broadcastElectionMessage(payload)

        // 2. Appliquer le cooldown seulement si le broadcast a réussi
        //    Évite de paralyser le nœud si la diffusion échoue
        if (rotation) {
            // Retrait appliqué même si la diffusion échoue : les membres détecteront
            // l'absence de Super-Pair par timeout et éliront un successeur.
            electionStateManager.startRotationYield(MandateRotationPolicy.ROTATION_YIELD_MS)
        } else if (broadcastResult.isSuccess) {
            electionStateManager.setCooldown(5 * 60 * 1000L)
        }

        return broadcastResult
    }
}
