package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.Leave
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.leaveSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SendLeaveUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val identityRepository: IdentityRepository,
    private val memberHeartbeatSender: IMemberHeartbeatSender,
    private val joinStateMachine: JoinStateMachine,
    private val networkEventRepository: NetworkEventRepository
) {
    // M14 + H25 : LEAVE doit s'envoyer même quand le serviceScope est en cours d'annulation
    // (onDestroy). withContext(NonCancellable) garantit que la signature + l'envoi WS aboutissent
    // avant que le service ne soit détruit. Sinon : le SP attend SP_TIMEOUT (90s) au lieu
    // d'évincer immédiatement.
    suspend operator fun invoke() = withContext(NonCancellable) {
        val state = joinStateMachine.currentState.value
        if (state !is NodeJoinState.Member) return@withContext
        val identity = identityRepository.getIdentity().getOrElse { return@withContext }
        val ts = System.currentTimeMillis()
        val nodeIdBytes = identity.nodeId.hexToByteArray()
        val signedBytes = leaveSignedBytes(nodeIdBytes, state.clusterId, ts)
        val signature = securityRepository.signData(signedBytes).getOrElse { return@withContext }
        val leave = Leave(nodeIdBytes, ts, signature)
        networkEventRepository.pushEvent(
            "[LEAVE-MEM] Envoi LEAVE au SP ${state.superPairNodeId.toHexShort()}"
        )
        memberHeartbeatSender.sendLeave(state.superPairNodeId, leave)
            .onFailure { /* best-effort — SP retombera sur timeout 90s */ }
    }
}
