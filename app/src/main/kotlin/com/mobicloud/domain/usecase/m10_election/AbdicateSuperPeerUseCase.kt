package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
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
    suspend operator fun invoke(): Result<Unit> {
        val identity = securityRepository.getIdentity().getOrElse {
            return Result.failure(it)
        }
        val score = trustScoreProvider.getTrustScore(identity.nodeId).toFloat()

        val dataToSign = "${identity.nodeId}:${ElectionMessageType.ABDICATION.name}".toByteArray()
        val signature = securityRepository.signData(dataToSign).getOrElse {
            return Result.failure(Exception("Failed to sign ABDICATION payload", it))
        }

        val payload = ElectionPayload(
            senderNodeId = identity.nodeId,
            type = ElectionMessageType.ABDICATION,
            reliabilityScore = score,
            signatureBytes = signature
        )

        // 1. Diffuser l'abdication en premier
        val broadcastResult = networkClient.broadcastElectionMessage(payload)

        // 2. Appliquer le cooldown seulement si le broadcast a réussi
        //    Évite de paralyser le nœud si la diffusion échoue
        if (broadcastResult.isSuccess) {
            electionStateManager.setCooldown(5 * 60 * 1000L)
        }

        return broadcastResult
    }
}
