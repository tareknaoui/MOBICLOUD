package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionEvent
import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.LocalRepairBuffer
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
    private val networkEventRepository: NetworkEventRepository
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
                // Vérifier la signature avant de rétrograder le Super-Pair actuel
                val dataToVerify = "${payload.senderNodeId}:${payload.type.name}".toByteArray()
                
                val senderPeer = peerRepository.peers.value
                    .find { it.identity.nodeId == payload.senderNodeId }

                if (senderPeer == null) {
                    return Result.failure(Exception("Received ABDICATION from unknown peer '${payload.senderNodeId}' — ignoring."))
                }

                // Check that the sender is actually the current Super-Peer
                if (!senderPeer.isSuperPair) {
                    networkEventRepository.pushEvent("WARNING: R\u00e9ception d'ABDICATION depuis '${payload.senderNodeId}' qui n'est pas Super-Pair \u2014 ignor\u00e9.")
                    return Result.success(ElectionEvent.Ignored)
                }

                val isValid = securityRepository.verifySignature(
                    data = dataToVerify,
                    signature = payload.signatureBytes,
                    publicKey = senderPeer.identity.publicKeyBytes
                ).getOrElse { error ->
                    return Result.failure(Exception("Signature verification failed for ABDICATION from '${payload.senderNodeId}'", error))
                }

                if (!isValid) {
                    return Result.failure(Exception("Invalid signature on ABDICATION message from '${payload.senderNodeId}' — ignoring."))
                }

                // Signature valide -> Rétrograder explicitement le statut Super-Pair pour déclencher uen nouvelle élection via RunBully
                peerRepository.clearSuperPairStatus(payload.senderNodeId)

                Result.success(ElectionEvent.AbdicationReceived(payload.senderNodeId))
            }

            ElectionMessageType.COORDINATOR -> {
                // F-04 : Vérifier la signature avant d'enregistrer le nouveau coordinateur.
                val dataToVerify = "${payload.senderNodeId}:${payload.type.name}".toByteArray()

                // F-03 : Récupérer la clé publique réelle depuis la PeerRegistry (A).
                //        Le pair est déjà connu via les Heartbeats de l'Epic 2.
                val senderPeer = peerRepository.peers.value
                    .find { it.identity.nodeId == payload.senderNodeId }

                if (senderPeer == null) {
                    // Pair inconnu — refuser silencieusement sans crasher
                    return Result.failure(
                        Exception("Received COORDINATOR from unknown peer '${payload.senderNodeId}' — ignoring.")
                    )
                }

                // F-04 : Vérification cryptographique de la signature
                val isValid = securityRepository.verifySignature(
                    data = dataToVerify,
                    signature = payload.signatureBytes,
                    publicKey = senderPeer.identity.publicKeyBytes
                ).getOrElse { error ->
                    return Result.failure(Exception("Signature verification failed for COORDINATOR from '${payload.senderNodeId}'", error))
                }

                if (!isValid) {
                    return Result.failure(
                        Exception("Invalid signature on COORDINATOR message from '${payload.senderNodeId}' — potential forgery, ignoring.")
                    )
                }

                // Signature valide → enregistrer le nouveau Super-Pair avec son vrai NodeIdentity
                peerRepository.registerOrUpdatePeer(
                    identity = senderPeer.identity,
                    timestampMs = System.currentTimeMillis(),
                    isSuperPair = true
                )

                // AC#6 : Drainer le buffer de réparation et notifier le RadarLogConsole
                val pendingRequests = localRepairBuffer.drain()
                if (pendingRequests.isNotEmpty()) {
                    networkEventRepository.pushEvent(
                        "[BUFFER] ${pendingRequests.size} requête(s) de réparation drainées (FIFO) → nouveau Super-Pair ${payload.senderNodeId.take(8)}"
                    )
                }
                // Future (Epic 7): Retransmettre ces requêtes au nouveau Super-Pair
                // pendingRequests.forEach { request -> ... }

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
        val dataToSign = "${identity.nodeId}:${type.name}".toByteArray()
        val signature = securityRepository.signData(dataToSign).getOrElse { error ->
            return Result.failure(Exception("Failed to sign ${type.name} payload", error))
        }
        return Result.success(
            ElectionPayload(
                senderNodeId = identity.nodeId,
                type = type,
                reliabilityScore = score,
                signatureBytes = signature
            )
        )
    }
}
