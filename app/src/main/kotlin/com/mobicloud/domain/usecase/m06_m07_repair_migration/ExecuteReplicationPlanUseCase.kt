package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.ReplicationPlanMessage
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.util.toSigHex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 7.3 — Exécuteur côté donneur : reçoit un `REPLICATE_PLAN` signé et retransmet
 * le bloc **chiffré** (sans déchiffrement — transfert aveugle opaque) vers la destination.
 */
@Singleton
class ExecuteReplicationPlanUseCase @Inject constructor(
    private val hostedBlockRepository: HostedBlockRepository,
    private val peerRepository: PeerRepository,
    private val securityRepository: SecurityRepository,
    private val blockSender: BlockSender,
    private val networkEventRepository: NetworkEventRepository
) : ReplicationPlanHandler {

    companion object {
        const val PER_BLOCK_TIMEOUT_MS = 4_000L  // cohérent avec Story 7.2
    }

    override suspend fun onReplicationPlanReceived(plan: ReplicationPlanMessage) {
        // 1) Vérifier que l'émetteur du plan est bien un Super-Pair connu
        val superPeer = peerRepository.peers.value
            .firstOrNull { it.identity.nodeId == plan.superPeerNodeId && it.isSuperPair }
        if (superPeer == null) {
            networkEventRepository.pushEvent(
                "[REPAIR] Plan reçu d'un non Super-Pair ${plan.superPeerNodeId.take(8)} — ignoré"
            )
            return
        }

        // 2) Vérifier la signature — même format que l'émetteur (TriggerAutoRepairUseCase)
        val d = plan.directive
        val sigPayload = buildString {
            append(plan.superPeerNodeId); append("|REPAIR|")
            append(d.blockId); append(":")
            append(d.destinationNodeId); append(":")
            append(d.destinationIp); append(":")
            append(d.destinationPort); append(":")
            append(d.destinationPublicKeyBytes.toSigHex())
        }.toByteArray()
        val valid = securityRepository.verifySignature(
            data = sigPayload,
            signature = plan.signatureBytes,
            publicKey = superPeer.identity.publicKeyBytes
        ).getOrDefault(false)
        if (!valid) {
            networkEventRepository.pushEvent("[REPAIR] Signature plan invalide — ignoré")
            return
        }

        // 3) Validation destination (défensive, aligne Story 7.2 round-2 hardening)
        //    Port doit être dans 1..65535 — sinon InetSocketAddress throw IllegalArgumentException non catchée.
        if (d.destinationIp.isBlank() || d.destinationPort !in 1..65535) {
            networkEventRepository.pushEvent(
                "[REPAIR] ${d.blockId.take(16)} — destination invalide (ip/port) — ignoré"
            )
            return
        }

        // 4) Garde anti-self : un SP compromis ne doit pas pouvoir ordonner à un donneur de
        //    s'auto-copier un bloc (self-connect + pollution DHT après gossip).
        val localId = securityRepository.getIdentity().getOrElse { return }.nodeId
        if (d.destinationNodeId == localId) {
            networkEventRepository.pushEvent(
                "[REPAIR] ${d.blockId.take(16)} — destination == self, directive ignorée"
            )
            return
        }

        // 5) AC#2 — lire le bloc CHIFFRÉ localement (zéro déchiffrement)
        val payload = hostedBlockRepository.getBlock(d.blockId).getOrNull()
        if (payload == null) {
            networkEventRepository.pushEvent(
                "[REPAIR] Bloc ${d.blockId.take(16)} absent localement — ignoré"
            )
            return
        }

        val destPeer = Peer(
            identity = NodeIdentity(
                nodeId = d.destinationNodeId,
                publicKeyBytes = d.destinationPublicKeyBytes
            ),
            lastSeenTimestampMs = System.currentTimeMillis(),
            ipAddress = d.destinationIp,
            port = d.destinationPort
        )

        val msg = BlockTransferMessage(
            blockId = payload.blockId,
            ownerId = localId,  // aligne avec Story 7.2 (donneur = sender)
            fragmentIndex = payload.fragmentIndex,
            isParity = payload.isParity,
            ciphertext = payload.ciphertext,  // AC#2 — ciphertext inchangé
            iv = payload.iv,                  // AC#2 — iv inchangé
            originalFileSize = 0L
        )
        blockSender.sendBlock(msg, destPeer, PER_BLOCK_TIMEOUT_MS)
            .onSuccess { ack ->
                // Vérifie que l'ACK provient bien de la destination annoncée (pas d'un attaquant à même IP/port).
                if (ack.receiverNodeId != d.destinationNodeId) {
                    networkEventRepository.pushEvent(
                        "[REPAIR] ${payload.blockId.take(16)} ACK émetteur ${ack.receiverNodeId.take(8)} ≠ destination ${d.destinationNodeId.take(8)} — suspect"
                    )
                } else {
                    networkEventRepository.pushEvent(
                        "[REPAIR] ${payload.blockId.take(16)} → ${ack.receiverNodeId.take(8)} confirmé"
                    )
                }
            }
            .onFailure {
                networkEventRepository.pushEvent(
                    "[REPAIR] ${payload.blockId.take(16)} → ${d.destinationNodeId.take(8)} échec : ${it.message}"
                )
            }
    }
}
