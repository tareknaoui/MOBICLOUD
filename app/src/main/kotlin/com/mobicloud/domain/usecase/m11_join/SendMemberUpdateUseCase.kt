package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.memberUpdateSignedBytes
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendMemberUpdateUseCase @Inject constructor(
    private val memberRegistry: MemberRegistry,
    private val memberHeartbeatSender: IMemberHeartbeatSender,
    private val securityRepository: SecurityRepository,
    private val identityRepository: IdentityRepository,
    private val networkEventRepository: NetworkEventRepository
) {
    suspend operator fun invoke(update: MemberUpdate) {
        val identity = identityRepository.getIdentity().getOrElse { return }
        val senderNodeId = identity.nodeId.hexToByteArray()

        val members = memberRegistry.list()
        // H8 : pour LEFT, on INCLUT le leaver dans le broadcast même s'il a déjà été retiré
        // du registre par MonitorMemberLiveness (markEvictedIfStale → status=EVICTED →
        // listActive ne le retourne plus). Le membre évincé reçoit son propre LEFT et peut
        // déclencher un re-JOIN immédiat plutôt que d'attendre SP_TIMEOUT (90 s) et de
        // continuer à envoyer des HB inutiles dans l'intervalle.
        // Soi-même (le SP émetteur) reste exclu : pas de boucle de message.
        val activeMemberIds = members
            .filter { !it.nodeId.contentEquals(senderNodeId) }
            .map { it.nodeId }
        val memberNodeIds = if (update.event == MemberUpdateEvent.LEFT
            && update.leftNodeId.isNotEmpty()
            && activeMemberIds.none { it.contentEquals(update.leftNodeId) }
            && !update.leftNodeId.contentEquals(senderNodeId)
        ) {
            activeMemberIds + listOf(update.leftNodeId)
        } else {
            activeMemberIds
        }
        if (memberNodeIds.isEmpty()) return

        val memberOrNodeIdHex = update.member?.nodeId?.toHexString()
            ?: update.leftNodeId.takeIf { it.isNotEmpty() }?.toHexString()
            ?: return
        val signedBytes = memberUpdateSignedBytes(
            senderNodeId = senderNodeId,
            event = update.event,
            memberOrNodeIdHex = memberOrNodeIdHex,
            ts = update.timestampMs
        )
        val signature = securityRepository.signData(signedBytes).getOrElse { return }
        val signedUpdate = update.copy(signatureBytes = signature)

        memberHeartbeatSender.broadcast(memberNodeIds, signedUpdate)
            .onFailure {
                networkEventRepository.pushEvent("[MEMBER-UPDATE] Broadcast échoué partiellement: ${it.message}")
            }
        // Trace conditionnelle pour distinguer JOINED (informatif) de LEFT (notif eviction inclus)
        if (update.event == MemberUpdateEvent.LEFT) {
            networkEventRepository.pushEvent(
                "[MEMBER-UPDATE] LEFT diffusé à ${memberNodeIds.size} membres (leaver inclus)"
            )
        }
    }
}
