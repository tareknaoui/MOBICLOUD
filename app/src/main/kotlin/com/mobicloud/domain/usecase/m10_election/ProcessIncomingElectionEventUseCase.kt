package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.ElectionEvent
import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.electionSignedBytes
import com.mobicloud.domain.models.m11_join.hexToByteArray
import kotlin.math.abs
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.MAX_RADIUS_METERS
import com.mobicloud.domain.usecase.m06_m07_repair_migration.LocalRepairBuffer
import com.mobicloud.domain.usecase.m11_join.JoinStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Traite en temps réel les événements du protocole Bully reçus par le réseau P2P.
 *
 * Retourne un [Flow] de [Result<ElectionEvent>] afin que l'appelant (ViewModel/Service)
 * puisse réagir sémantiquement à chaque type d'événement :
 *  - [ElectionEvent.ShouldStartOwnElection] → déclencher [RunBullyElectionUseCase] (AC4)
 *  - [ElectionEvent.CoordinatorRegistered]  → mettre à jour l'UI
 *  - [ElectionEvent.AliveReceived]          → aucune action requise (géré par RunBully)
 */
class ProcessIncomingElectionEventUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val trustScoreProvider: ITrustScoreProvider,
    private val peerRepository: PeerRepository,
    private val networkClient: IElectionNetworkClient,
    private val electionStateManager: ElectionStateManager,
    private val localRepairBuffer: LocalRepairBuffer,
    private val networkEventRepository: NetworkEventRepository,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val wifiNetworkRepository: WifiNetworkRepository,
    private val joinStateMachine: JoinStateMachine? = null
) {
    operator fun invoke(): Flow<Result<ElectionEvent>> {
        return networkClient.incomingMessages.map { payload ->
            try {
                processPayload(payload)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * F-05 : [securityRepository.getIdentity()] est appelé via [getOrElse] explicite —
     * toute erreur lève une exception catchée par le bloc try/catch du collecteur.
     */
    private suspend fun processPayload(payload: ElectionPayload): Result<ElectionEvent> {
        val localIdentity = securityRepository.getIdentity().getOrElse { error ->
            throw IllegalStateException("Cannot retrieve local identity to process election event", error)
        }
        val localScore = trustScoreProvider.getTrustScore(localIdentity.nodeId).toFloat()

        // Garde anti-replay : tout payload Bully doit etre dans la fenetre +/-30s.
        // Bloque le rejeu d'un message capture d'une session precedente.
        val skewMs = abs(System.currentTimeMillis() - payload.timestampMs)
        if (skewMs > BULLY_TIMESTAMP_WINDOW_MS) {
            return Result.failure(
                Exception("Bully payload ${payload.type.name} hors fenetre (skew=${skewMs}ms) -- ignore (replay suspect).")
            )
        }

        // Verification de signature centralisee pour TOUS les types.
        // Auparavant ELECTION/ALIVE etaient acceptes sans verification : un attaquant
        // pouvait forger un message avec un score gonfle et gagner l'election.
        val verified = verifyPayloadSignature(payload)
        if (verified.isFailure) {
            return Result.failure(verified.exceptionOrNull() ?: Exception("Signature verification failed"))
        }

        return when (payload.type) {

            ElectionMessageType.ELECTION -> {
                if (electionStateManager.isInCooldown()) {
                    // En cooldown — règle métier : ignorer silencieusement toute élection entrante
                    Result.success(ElectionEvent.Ignored)
                } else if (isHigherPriority(localScore, localIdentity.nodeId, payload.reliabilityScore, payload.senderNodeId)) {
                    // Étape 1 : Répondre ALIVE (le pair émetteur a un score inférieur)
                    val alivePayload = createPayload(localIdentity, localScore, ElectionMessageType.ALIVE)
                        .getOrElse { error ->
                            throw Exception("Failed to sign ALIVE payload", error)
                        }
                    networkClient.broadcastElectionMessage(alivePayload).getOrElse { error ->
                        throw Exception("Failed to broadcast ALIVE message", error)
                    }
                    // Étape 2 : Signaler à l'appelant qu'il doit lancer sa propre candidature (AC4)
                    Result.success(ElectionEvent.ShouldStartOwnElection)
                } else {
                    // Score local inférieur — règle AC5 : rester silencieux
                    Result.success(ElectionEvent.Ignored)
                }
            }

            ElectionMessageType.ALIVE -> {
                // Traité par RunBullyElectionUseCase via le SharedFlow ; aucune action ici.
                Result.success(ElectionEvent.AliveReceived)
            }

            ElectionMessageType.ABDICATION -> {
                val senderPeer = peerRepository.peers.value
                    .find { it.identity.nodeId == payload.senderNodeId }
                    ?: return Result.failure(Exception("Received ABDICATION from unknown peer '${payload.senderNodeId}' -- ignoring."))

                // Check that the sender is actually the current Super-Peer
                if (!senderPeer.isSuperPair) {
                    networkEventRepository.pushEvent("WARNING: R\u00e9ception d'ABDICATION depuis '${payload.senderNodeId}' qui n'est pas Super-Pair \u2014 ignor\u00e9.")
                    return Result.success(ElectionEvent.Ignored)
                }

                // Signature deja verifiee en haut -- Retrograder le statut Super-Pair pour
                // declencher une nouvelle election via RunBully.
                peerRepository.clearSuperPairStatus(payload.senderNodeId)

                Result.success(ElectionEvent.AbdicationReceived(payload.senderNodeId))
            }

            ElectionMessageType.COORDINATOR -> {
                // F-03 : Recuperer la cle publique depuis la PeerRegistry (deja connu via heartbeats Epic 2).
                // La signature a deja ete verifiee en haut de processPayload.
                val senderPeer = peerRepository.peers.value
                    .find { it.identity.nodeId == payload.senderNodeId }
                    ?: return Result.failure(
                        Exception("Received COORDINATOR from unknown peer '${payload.senderNodeId}' -- ignoring.")
                    )

                // FIX SPLIT-CLUSTER : evaluer WG1 sur le clusterId LIVE du SSID,
                // pas sur la DB. Sinon un stale en DB (ex : ancien WiFi domestique)
                // ferait rejeter a tort un COORDINATOR legitime du WiFi actuel.
                val localWifiClusterId = nodeSettingsRepository.getCurrentWifiClusterId()
                val localOnWifi = localWifiClusterId.isNotEmpty()
                if (payload.clusterId.isNotBlank() && localWifiClusterId.isNotBlank()
                    && payload.clusterId != localWifiClusterId && localOnWifi
                ) {
                    return Result.failure(
                        Exception(
                            "COORDINATOR from cluster '${payload.clusterId.take(8)}' rejected " +
                            "— local WiFi cluster '${localWifiClusterId.take(8)}'"
                        )
                    )
                }

                // Signature valide → enregistrer le nouveau Super-Pair avec son vrai NodeIdentity
                peerRepository.registerOrUpdatePeer(
                    identity = senderPeer.identity,
                    timestampMs = System.currentTimeMillis(),
                    isSuperPair = true
                )

                // AC3 — adopter le clusterId si : payload non blank, pas le nœud local,
                //        et nœud non connecté en WiFi (4G adopte le cluster du coordinateur)
                // AC5 — blank = legacy node, ignorer
                // AC6 — ne pas mettre à jour si le COORDINATOR vient du nœud local lui-même
                if (payload.clusterId.isNotBlank() && payload.senderNodeId != localIdentity.nodeId && !localOnWifi) {
                    nodeSettingsRepository.updateClusterId(payload.clusterId)
                }

                // AC#6 : Drainer le buffer de réparation et notifier le RadarLogConsole
                val pendingRequests = localRepairBuffer.drain()
                if (pendingRequests.isNotEmpty()) {
                    networkEventRepository.pushEvent(
                        "[BUFFER] ${pendingRequests.size} requête(s) de réparation drainées (FIFO) → nouveau Super-Pair ${payload.senderNodeId.take(8)}"
                    )
                }
                // Future (Epic 7): Retransmettre ces requêtes au nouveau Super-Pair
                // pendingRequests.forEach { request -> ... }

                // AC11 — déclencher le protocole JOIN si :
                //   (a) le clusterId reçu diffère du nôtre (un cluster voisin annonce son SP), OU
                //   (b) c'est le même cluster mais un autre nœud que self (changement de SP intra-cluster).
                // Le cas (senderNodeId == self) avec même clusterId est exclu (auto-victoire,
                // câblage déjà géré par RunBullyElectionUseCase).
                val localClusterId = runCatching { nodeSettingsRepository.getSettings().clusterId }.getOrDefault("")
                val differentCluster = payload.clusterId != localClusterId
                val differentSenderSameCluster =
                    payload.clusterId == localClusterId && payload.senderNodeId != localIdentity.nodeId
                if (differentCluster || differentSenderSameCluster) {
                    joinStateMachine?.transition(
                        JoinEvent.CoordinatorReceived(
                            senderNodeId = payload.senderNodeId.hexToByteArray(),
                            clusterId = payload.clusterId,
                            gpsLatitude = payload.gpsLatitude,
                            gpsLongitude = payload.gpsLongitude,
                            maxRadiusMeters = payload.maxRadiusMeters
                        )
                    )
                }

                Result.success(ElectionEvent.CoordinatorRegistered(payload.senderNodeId))
            }
        }
    }

    /**
     * Retourne `true` si le nœud LOCAL est prioritaire sur le nœud DISTANT.
     * Utilisé pour décider si on répond ALIVE à un message ELECTION reçu.
     */
    private fun isHigherPriority(
        localScore: Float,
        localId: String,
        otherScore: Float,
        otherId: String
    ): Boolean {
        if (localScore > otherScore) return true
        if (localScore < otherScore) return false
        return localId > otherId
    }

    /**
     * F-02 : Retourne [Result.failure] si la signature échoue — aucun payload
     * non signé n'est jamais broadcasté.
     */
    private suspend fun createPayload(
        identity: NodeIdentity,
        score: Float,
        type: ElectionMessageType
    ): Result<ElectionPayload> {
        val timestampMs = System.currentTimeMillis()
        val dataToSign = electionSignedBytes(type, identity.nodeId, score, "", timestampMs)
        val signature = securityRepository.signData(dataToSign).getOrElse { error ->
            return Result.failure(Exception("Failed to sign ${type.name} payload", error))
        }
        return Result.success(
            ElectionPayload(
                senderNodeId = identity.nodeId,
                type = type,
                reliabilityScore = score,
                signatureBytes = signature,
                timestampMs = timestampMs
            )
        )
    }

    /**
     * Verifie la signature d'un payload Bully entrant.
     * Le sender doit deja etre connu via PeerRegistry (heartbeats) -- les payloads
     * de pairs inconnus sont rejetes avant d'arriver dans le protocole.
     *
     * Centralise la verif pour les 4 types de message ; auparavant ELECTION/ALIVE
     * etaient acceptes sans signature, permettant le forge avec score gonfle.
     */
    private suspend fun verifyPayloadSignature(payload: ElectionPayload): Result<Unit> {
        val senderPeer = peerRepository.peers.value
            .find { it.identity.nodeId == payload.senderNodeId }
            ?: return Result.failure(
                Exception("Bully ${payload.type.name} from unknown peer '${payload.senderNodeId}' -- ignored.")
            )

        // Tenter d'abord v2 (avec champs GPS), puis fallback v1 pour rétrocompat pairs legacy
        val dataToVerifyV2 = electionSignedBytes(
            type = payload.type,
            senderNodeId = payload.senderNodeId,
            reliabilityScore = payload.reliabilityScore,
            clusterId = payload.clusterId,
            timestampMs = payload.timestampMs,
            gpsLatitude = payload.gpsLatitude,
            gpsLongitude = payload.gpsLongitude,
            maxRadiusMeters = payload.maxRadiusMeters
        )

        // Story 11.2 décision review : v2 strict. Le fallback v1 a été retiré pour empêcher
        // qu'un attaquant envoie un payload v2 (GPS arbitraire) signé en v1 valide.
        val isValidV2 = securityRepository.verifySignature(
            data = dataToVerifyV2,
            signature = payload.signatureBytes,
            publicKey = senderPeer.identity.publicKeyBytes
        ).getOrElse { error ->
            return Result.failure(
                Exception("Signature verification failed for ${payload.type.name} from '${payload.senderNodeId}'", error)
            )
        }

        return if (isValidV2) Result.success(Unit)
        else Result.failure(
            Exception("Invalid signature on ${payload.type.name} from '${payload.senderNodeId}' -- potential forgery, ignored.")
        )
    }
}
