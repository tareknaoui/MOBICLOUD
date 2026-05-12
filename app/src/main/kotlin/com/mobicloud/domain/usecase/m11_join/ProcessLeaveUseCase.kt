package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.m11_join.Leave
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.leaveSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject

class ProcessLeaveUseCase @Inject constructor(
    private val memberDao: MemberDao,
    private val securityRepository: SecurityRepository,
    private val joinStateMachine: JoinStateMachine,
    private val sendMemberUpdateUseCase: SendMemberUpdateUseCase,
    private val networkEventRepository: NetworkEventRepository
) {
    suspend operator fun invoke(leave: Leave): Result<Unit> {
        val state = joinStateMachine.currentState.value
        if (state !is NodeJoinState.SuperPair) return Result.success(Unit)

        val nodeIdHex = leave.senderNodeId.toHexString()
        val nodeIdShort = leave.senderNodeId.toHexShort()

        val memberEntity = memberDao.findByNodeId(nodeIdHex)
        if (memberEntity == null) {
            networkEventRepository.pushEvent("[LEAVE-SP] WARN Leave de nodeId inconnu $nodeIdShort — ignoré")
            return Result.failure(UnknownMemberException(nodeIdHex))
        }

        // ProcessLeave est invoqué côté SP, donc state.clusterId est le clusterId du SP courant.
        val signedBytes = leaveSignedBytes(leave.senderNodeId, state.clusterId, leave.timestampMs)
        val valid = securityRepository.verifySignature(signedBytes, leave.signatureBytes, memberEntity.publicKeyBytes)
            .getOrElse { return Result.failure(it) }
        if (!valid) return Result.failure(Exception("Signature LEAVE invalide"))

        val now = System.currentTimeMillis()
        // Cf. ProcessHeartbeatUseCase : éviter abs() overflow sur ts attaquant-contrôlé.
        if (leave.timestampMs < now - BULLY_TIMESTAMP_WINDOW_MS || leave.timestampMs > now + BULLY_TIMESTAMP_WINDOW_MS) {
            return Result.failure(StaleTimestampException(leave.timestampMs, now))
        }

        memberDao.deleteByNodeId(nodeIdHex, state.clusterId)
        networkEventRepository.pushEvent("[LEAVE-SP] Membre $nodeIdShort a quitté gracieusement")

        sendMemberUpdateUseCase.invoke(
            MemberUpdate(
                event = MemberUpdateEvent.LEFT,
                member = null,
                leftNodeId = leave.senderNodeId,
                timestampMs = now,
                signatureBytes = byteArrayOf()
            )
        )
        return Result.success(Unit)
    }
}
