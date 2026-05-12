package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Invoqué après victoire Bully (depuis [RunBullyElectionUseCase]) ou depuis
 * [BullySoloElectionUseCase] après expiration de `ISOLATION_BACKOFF_MS`.
 *
 * - Initialise le [MemberRegistry] à `[MemberInfo(self, role=SUPER_PAIR)]`
 * - Persiste le [clusterId] dans [NodeSettingsRepository]
 * - Émet [JoinEvent.BullyVictory] → [JoinStateMachine] transite vers `SuperPair`
 * - TODO Story 11.3 : démarrer MonitorMemberLivenessUseCase (placeholder no-op acceptable en 11.2)
 */
class MarkSelfAsSuperPairUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val peerRepository: PeerRepository,
    private val memberRegistry: MemberRegistry,
    private val joinStateMachine: JoinStateMachine
) {
    suspend operator fun invoke(clusterId: String) {
        val identity = securityRepository.getIdentity().getOrElse { return }
        val freeBytes = runCatching { nodeSettingsRepository.observeFreeSpaceBytes().first() }.getOrDefault(0L)

        // ipAddress/port restent vides : peuplés par les heartbeats Story 11.3
        // (cohérent avec MemberInfo des autres pairs : c'est le heartbeat qui annonce
        // l'IP/port effectifs, pas l'auto-promotion).
        val selfMember = MemberInfo(
            nodeId = identity.nodeId.hexToByteArray(),
            publicKey = identity.publicKeyBytes,
            ipAddress = "",
            port = 0,
            freeBytes = freeBytes,
            role = MemberRole.SUPER_PAIR
        )
        memberRegistry.add(selfMember)

        // Si la persistance échoue, on rollback l'ajout au registre et on n'avance pas la FSM :
        // sinon le service redémarre avec FSM=SuperPair mais clusterId stale en DB → split-brain.
        val persisted = runCatching { nodeSettingsRepository.updateClusterId(clusterId) }
        if (persisted.isFailure) {
            memberRegistry.remove(selfMember.nodeId)
            return
        }

        joinStateMachine.transition(JoinEvent.BullyVictory(clusterId))

        // TODO Story 11.3 : MonitorMemberLivenessUseCase.start() — placeholder no-op
    }
}
