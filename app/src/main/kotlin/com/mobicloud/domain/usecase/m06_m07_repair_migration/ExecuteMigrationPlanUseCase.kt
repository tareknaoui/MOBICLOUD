package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.MigrateBlockDirective
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecuteMigrationPlanUseCase @Inject constructor(
    private val hostedBlockRepository: HostedBlockRepository,
    private val peerRepository: PeerRepository,
    private val securityRepository: SecurityRepository,
    private val blockSender: BlockSender,
    private val networkEventRepository: NetworkEventRepository
) : MigrationPlanHandler {

    companion object {
        // < 5s NFR-02, laisse marge pour MàJ DHT côté Super-Pair
        const val PER_BLOCK_TIMEOUT_MS = 4_000L
    }

    override suspend fun onMigrationPlanReceived(plan: MigrationPlanMessage) = coroutineScope {
        // 1) Vérification signature du plan avec la clé publique du Super-Pair annoncé
        val superPeer = peerRepository.peers.value
            .firstOrNull { it.identity.nodeId == plan.superPeerNodeId && it.isSuperPair }
        if (superPeer == null) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Plan reçu d'un nœud non Super-Pair ${plan.superPeerNodeId.take(8)} — ignoré"
            )
            return@coroutineScope
        }
        val planSigPayload = "${plan.superPeerNodeId}|${plan.directives.joinToString("|") { "${it.blockId}:${it.destinationNodeId}" }}"
            .toByteArray()
        val valid = securityRepository.verifySignature(
            data = planSigPayload,
            signature = plan.signatureBytes,
            publicKey = superPeer.identity.publicKeyBytes
        ).getOrDefault(false)
        if (!valid) {
            networkEventRepository.pushEvent("[MIGRATION] Signature plan invalide — ignoré")
            return@coroutineScope
        }

        val localId = securityRepository.getIdentity().getOrElse {
            networkEventRepository.pushEvent("[MIGRATION] identité locale indisponible — plan ignoré")
            return@coroutineScope
        }.nodeId

        // 2) Exécution parallèle des directives — AC#2 transfert aveugle, AC#3 ACK signé vérifié par BlockSender
        plan.directives.map { directive ->
            async {
                executeDirective(directive, localId)
            }
        }.awaitAll()
        Unit
    }

    private suspend fun executeDirective(directive: MigrateBlockDirective, localNodeId: String) {
        // AC#2 : on lit le bloc déjà chiffré — pas de déchiffrement, transfert opaque
        val payload = hostedBlockRepository.getBlock(directive.blockId).getOrNull()
        if (payload == null) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Bloc ${directive.blockId.take(16)} absent localement — ignoré"
            )
            return
        }
        val destPeer = Peer(
            identity = NodeIdentity(
                nodeId = directive.destinationNodeId,
                publicKeyBytes = directive.destinationPublicKeyBytes
            ),
            lastSeenTimestampMs = System.currentTimeMillis(),
            ipAddress = directive.destinationIp,
            port = directive.destinationPort
        )
        val msg = BlockTransferMessage(
            blockId = payload.blockId,
            ownerId = localNodeId,  // owner conservé = nœud partant (propriétaire d'origine)
            fragmentIndex = payload.fragmentIndex,
            isParity = payload.isParity,
            ciphertext = payload.ciphertext,
            iv = payload.iv,
            originalFileSize = 0L  // inconnu côté hébergeur, non-bloquant pour réception
        )
        // AC#3 : BlockSender.sendBlock (impl BlockTransferClient) vérifie la signature de l'ACK
        blockSender.sendBlock(msg, destPeer, PER_BLOCK_TIMEOUT_MS)
            .onSuccess { ack ->
                networkEventRepository.pushEvent(
                    "[MIGRATION] ${payload.blockId.take(16)} → ${ack.receiverNodeId.take(8)} confirmé"
                )
            }
            .onFailure {
                networkEventRepository.pushEvent(
                    "[MIGRATION] ${payload.blockId.take(16)} → ${directive.destinationNodeId.take(8)} échec : ${it.message}"
                )
            }
    }
}
