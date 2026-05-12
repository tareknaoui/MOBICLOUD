package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import dagger.Lazy
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Invoqué après victoire Bully (depuis [RunBullyElectionUseCase]) ou depuis
 * [BullySoloElectionUseCase] après expiration de `ISOLATION_BACKOFF_MS`.
 *
 * - Repeuple [MemberRegistry] depuis le snapshot mémoire (FR-11.8 continuité post-Bully)
 * - Persiste le [clusterId] dans [NodeSettingsRepository]
 * - Émet [JoinEvent.BullyVictory] → [JoinStateMachine] transite vers `SuperPair`
 * - Démarre [MonitorMemberLivenessUseCase] (Story 11.3)
 */
class MarkSelfAsSuperPairUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val peerRepository: PeerRepository,
    private val memberRegistry: MemberRegistry,
    private val joinStateMachine: JoinStateMachine,
    private val monitorMemberLivenessUseCaseLazy: Lazy<MonitorMemberLivenessUseCase>,
    private val memberSnapshotCacheUseCaseLazy: Lazy<MemberSnapshotCacheUseCase>
) {
    suspend operator fun invoke(clusterId: String) {
        val identity = securityRepository.getIdentity().getOrElse { return }
        val freeBytes = runCatching { nodeSettingsRepository.observeFreeSpaceBytes().first() }.getOrDefault(0L)

        val selfNodeId = identity.nodeId.hexToByteArray()
        val selfMember = MemberInfo(
            nodeId = selfNodeId,
            publicKey = identity.publicKeyBytes,
            ipAddress = "",
            port = 0,
            freeBytes = freeBytes,
            role = MemberRole.SUPER_PAIR
        )
        memberRegistry.add(selfMember)

        // Repeuplement depuis snapshot (FR-11.8) : les membres connus restent dans le cluster
        // sans re-JOIN après mort de l'ancien SP.
        val snapshot = memberSnapshotCacheUseCaseLazy.get().snapshot()
        snapshot.filter { !it.nodeId.contentEquals(selfNodeId) }
                .forEach { memberRegistry.add(it) }

        val persisted = runCatching { nodeSettingsRepository.updateClusterId(clusterId) }
        if (persisted.isFailure) {
            memberRegistry.remove(selfMember.nodeId, clusterId)
            return
        }

        joinStateMachine.transition(JoinEvent.BullyVictory(clusterId))

        monitorMemberLivenessUseCaseLazy.get().start()
    }
}
