package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.data.p2p.tcp.TcpConnectionManager
import com.mobicloud.domain.models.DepartureNoticeMessage
import com.mobicloud.domain.models.MigrateBlockDirective
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.PLAN_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.repository.DhtRepository
import kotlin.math.abs
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.util.toSigHex
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrchestrateBlockMigrationUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val dhtRepository: DhtRepository,
    private val securityRepository: SecurityRepository,
    private val tcpConnectionManager: TcpConnectionManager,
    private val gossipSyncUseCase: GossipSyncUseCase,
    private val networkEventRepository: NetworkEventRepository
) : DepartureNoticeHandler {

    companion object {
        const val NFR02_BUDGET_MS = 5_000L
    }

    override suspend fun onDepartureNoticeReceived(notice: DepartureNoticeMessage) {
        val identity = securityRepository.getIdentity().getOrElse {
            networkEventRepository.pushEvent("[MIGRATION] identité locale indisponible — plan annulé")
            return
        }

        // Snapshot unique de peerRepository.peers pour cohérence des étapes 1→3
        val peersSnapshot = peerRepository.peers.value

        // 1) Le nœud courant DOIT être Super-Pair pour orchestrer (sinon le NOTICE a été mal-routé)
        val selfIsSuperPair = peersSnapshot
            .any { it.identity.nodeId == identity.nodeId && it.isSuperPair && it.isActive }
        if (!selfIsSuperPair) {
            networkEventRepository.pushEvent("[MIGRATION] DEPARTURE_NOTICE ignoré — nœud local non Super-Pair")
            return
        }

        // 2) Vérification signature du NOTICE (le nœud partant signe "$nodeId:$blockIdsJoined")
        val departingPeer = peersSnapshot
            .firstOrNull { it.identity.nodeId == notice.senderNodeId }
        if (departingPeer == null) {
            networkEventRepository.pushEvent("[MIGRATION] Émetteur ${notice.senderNodeId.take(8)} inconnu — plan annulé")
            return
        }
        // Anti-replay : rejeter les NOTICE hors fenetre +/-30s.
        val skewMs = abs(System.currentTimeMillis() - notice.timestampMs)
        if (skewMs > PLAN_TIMESTAMP_WINDOW_MS) {
            networkEventRepository.pushEvent(
                "[MIGRATION] DEPARTURE_NOTICE hors fenetre (skew=${skewMs}ms) -- replay suspect, ignore"
            )
            return
        }
        val signedPayload = "${notice.senderNodeId}:${notice.hostedBlockIds.joinToString(",")}|ts=${notice.timestampMs}".toByteArray()
        val valid = securityRepository.verifySignature(
            data = signedPayload,
            signature = notice.signatureBytes,
            publicKey = departingPeer.identity.publicKeyBytes
        ).getOrDefault(false)
        if (!valid) {
            networkEventRepository.pushEvent("[MIGRATION] Signature DEPARTURE_NOTICE invalide — plan annulé")
            return
        }

        // Dedup défensif : un NOTICE conforme (HostedBlockDao.getAllBlockIds = DISTINCT PK) ne contient pas
        // de doublons ; s'en trouver indique un pair compromis ou un bug. On préserve l'ordre de première apparition.
        val uniqueBlockIds = notice.hostedBlockIds.distinct()
        if (uniqueBlockIds.size < notice.hostedBlockIds.size) {
            networkEventRepository.pushEvent(
                "[MIGRATION] Doublons détectés dans NOTICE ${notice.senderNodeId.take(8)} — dédupliqués"
            )
        }

        if (uniqueBlockIds.isEmpty()) {
            networkEventRepository.pushEvent("[MIGRATION] ${notice.senderNodeId.take(8)} — aucun bloc à migrer")
            return
        }

        // 3) Candidats destination : actifs, hors émetteur, hors soi-même, avec ip non-blank et port valide
        //    (défauts Protobuf ip="", port=0 et ports hors plage seraient rejetés côté exécuteur — autant filtrer ici)
        val candidates = peersSnapshot.filter { p ->
            p.isActive &&
            p.ipAddress?.isNotBlank() == true &&
            (p.port ?: 0) in 1..65535 &&
            p.identity.nodeId != notice.senderNodeId &&
            p.identity.nodeId != identity.nodeId
        }
        if (candidates.isEmpty()) {
            networkEventRepository.pushEvent("[MIGRATION] Aucun nœud de destination disponible — plan annulé")
            return
        }

        // 4) Round-robin sur blockIds (ordre stable préservé du NOTICE)
        val directives = uniqueBlockIds.mapIndexed { i, blockId ->
            val dest = candidates[i % candidates.size]
            MigrateBlockDirective(
                blockId = blockId,
                destinationNodeId = dest.identity.nodeId,
                destinationIp = dest.ipAddress!!,
                destinationPort = dest.port!!,
                destinationPublicKeyBytes = dest.identity.publicKeyBytes
            )
        }

        // 5) Signature du plan (domain separation — payload durci : inclut IP/port/pubkey des destinations
        //    pour empêcher une réécriture MITM qui redirigerait le transfert opaque vers un pair contrôlé)
        val planTimestampMs = System.currentTimeMillis()
        val planSigPayload = "${identity.nodeId}|${directives.joinToString("|") {
            "${it.blockId}:${it.destinationNodeId}:${it.destinationIp}:${it.destinationPort}:${it.destinationPublicKeyBytes.toSigHex()}"
        }}|ts=$planTimestampMs".toByteArray()
        val planSignature = securityRepository.signData(planSigPayload).getOrElse {
            networkEventRepository.pushEvent("[MIGRATION] Signature du plan échouée — plan annulé")
            return
        }

        val plan = MigrationPlanMessage(
            superPeerNodeId = identity.nodeId,
            directives = directives,
            signatureBytes = planSignature,
            timestampMs = planTimestampMs
        )

        // 6) Transmission du plan au nœud partant — AC#5 budget NFR-02 5s global
        val departingIp = departingPeer.ipAddress
        val departingPort = departingPeer.port
        if (departingIp == null || departingPort == null) {
            networkEventRepository.pushEvent("[MIGRATION] Adresse partant ${notice.senderNodeId.take(8)} inconnue — plan annulé")
            return
        }
        withTimeoutOrNull(NFR02_BUDGET_MS) {
            tcpConnectionManager.sendMigrationPlan(plan, departingIp, departingPort)
                .onSuccess {
                    networkEventRepository.pushEvent(
                        "[MIGRATION] Plan envoyé à ${notice.senderNodeId.take(8)} — ${directives.size} directive(s)"
                    )
                }
                .onFailure {
                    networkEventRepository.pushEvent("[MIGRATION] Envoi du plan échoué : ${it.message}")
                }
        } ?: networkEventRepository.pushEvent("[MIGRATION] Timeout envoi du plan (> 5s)")

        // 7) AC#4 — MàJ DHT optimiste : supprimer entrées du nœud partant AVANT d'insérer les nouvelles
        dhtRepository.deleteByNodeId(notice.senderNodeId)
            .onFailure { networkEventRepository.pushEvent("[MIGRATION] Suppression DHT partant échouée : ${it.message}") }
        directives.forEach { d ->
            dhtRepository.insertEntry(d.blockId, d.destinationNodeId, d.destinationIp, d.destinationPort)
                .onFailure {
                    networkEventRepository.pushEvent(
                        "[MIGRATION] Insert DHT ${d.blockId.take(16)}→${d.destinationNodeId.take(8)} échoué : ${it.message}"
                    )
                }
        }

        // 8) AC#4 — Gossip immédiat pour propagation du nouveau propriétaire
        gossipSyncUseCase.runGossipCycle()
            .onFailure { networkEventRepository.pushEvent("[MIGRATION] Gossip post-migration échoué : ${it.message}") }
    }
}
