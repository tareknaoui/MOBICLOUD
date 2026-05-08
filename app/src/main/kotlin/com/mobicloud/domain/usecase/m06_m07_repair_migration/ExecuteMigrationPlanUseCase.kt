package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.MigrateBlockDirective
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.PLAN_TIMESTAMP_WINDOW_MS
import kotlin.math.abs
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.util.toSigHex
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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

    override suspend fun onMigrationPlanReceived(plan: MigrationPlanMessage) = supervisorScope {
        // 1) Anti-replay : rejeter les plans hors fenetre +/-30s.
        val skewMs = abs(System.currentTimeMillis() - plan.timestampMs)
        if (skewMs > PLAN_TIMESTAMP_WINDOW_MS) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Plan hors fenetre (skew=${skewMs}ms) -- replay suspect, ignore"
            )
            return@supervisorScope
        }

        // 2) Vérification signature du plan avec la clé publique du Super-Pair annoncé
        val superPeer = peerRepository.peers.value
            .firstOrNull { it.identity.nodeId == plan.superPeerNodeId && it.isSuperPair }
        if (superPeer == null) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Plan reçu d'un nœud non Super-Pair ${plan.superPeerNodeId.take(8)} — ignoré"
            )
            return@supervisorScope
        }
        // Payload durci : cohérent avec OrchestrateBlockMigrationUseCase — inclut IP/port/pubkey destination + timestamp
        val planSigPayload = "${plan.superPeerNodeId}|${plan.directives.joinToString("|") {
            "${it.blockId}:${it.destinationNodeId}:${it.destinationIp}:${it.destinationPort}:${it.destinationPublicKeyBytes.toSigHex()}"
        }}|ts=${plan.timestampMs}".toByteArray()
        val valid = securityRepository.verifySignature(
            data = planSigPayload,
            signature = plan.signatureBytes,
            publicKey = superPeer.identity.publicKeyBytes
        ).getOrDefault(false)
        if (!valid) {
            networkEventRepository.pushEvent("[MIGRATION] Signature plan invalide — ignoré")
            return@supervisorScope
        }

        val localId = securityRepository.getIdentity().getOrElse {
            networkEventRepository.pushEvent("[MIGRATION] identité locale indisponible — plan ignoré")
            return@supervisorScope
        }.nodeId

        if (plan.directives.isEmpty()) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Plan vide reçu de ${plan.superPeerNodeId.take(8)} — aucune action"
            )
            return@supervisorScope
        }

        // 2) Exécution parallèle des directives — AC#2 transfert aveugle, AC#3 ACK signé vérifié par BlockSender.
        //    supervisorScope : un throw non-Result dans une directive n'annule pas ses sœurs.
        plan.directives.map { directive ->
            async {
                executeDirective(directive, localId)
            }
        }.awaitAll()
        Unit
    }

    private suspend fun executeDirective(directive: MigrateBlockDirective, localNodeId: String) {
        // Garde défensive : port hors plage valide, IP vide, ou destination == self
        // (plan signé par un SP compromis pouvant rediriger un bloc vers le pair lui-même).
        if (directive.destinationIp.isBlank() || directive.destinationPort !in 1..65535) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Directive ${directive.blockId.take(16)} → ${directive.destinationNodeId.take(8)} ignorée (adresse invalide)"
            )
            return
        }
        if (directive.destinationNodeId == localNodeId) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Directive ${directive.blockId.take(16)} ignorée — destination == self"
            )
            return
        }
        // AC#2 : on lit le bloc déjà chiffré — pas de déchiffrement, transfert opaque
        val result = hostedBlockRepository.getBlock(directive.blockId)
        val payload = result.fold(
            onSuccess = { it },
            onFailure = { err ->
                networkEventRepository.pushEvent(
                    "[MIGRATION] Lecture bloc ${directive.blockId.take(16)} échouée : ${err.message}"
                )
                null
            }
        )
        if (payload == null) {
            // Distingue succès-null (bloc réellement absent) du chemin failure déjà loggué ci-dessus.
            if (result.isSuccess) {
                networkEventRepository.pushEvent(
                    "[MIGRATION] Bloc ${directive.blockId.take(16)} absent localement — ignoré"
                )
            }
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
