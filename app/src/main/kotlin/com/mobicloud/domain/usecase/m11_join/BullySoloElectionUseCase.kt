package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Variante solo de l'élection Bully utilisée quand le nœud est en état [NodeJoinState.Isolated]
 * après [ISOLATION_BACKOFF_MS]. Court-circuite la phase d'émission `ELECTION` (aucun pair
 * joignable par définition de l'isolement).
 *
 * Garde-fou : si l'état n'est plus [Isolated] à l'invocation (NewCandidateDetected est arrivé
 * entre-temps), no-op (anti-cascade).
 */
class BullySoloElectionUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val peerRepository: PeerRepository,
    private val markSelfAsSuperPairUseCase: MarkSelfAsSuperPairUseCase,
    private val joinStateMachine: JoinStateMachine
) {
    suspend operator fun invoke() {
        val currentState = joinStateMachine.currentState.value
        if (currentState !is NodeJoinState.Isolated) return

        val newClusterId = UUID.randomUUID().toString()

        val identity = securityRepository.getIdentity().getOrElse { return }
        peerRepository.registerOrUpdatePeer(
            identity = identity,
            timestampMs = System.currentTimeMillis(),
            isSuperPair = true
        )

        markSelfAsSuperPairUseCase.invoke(newClusterId)
    }
}
