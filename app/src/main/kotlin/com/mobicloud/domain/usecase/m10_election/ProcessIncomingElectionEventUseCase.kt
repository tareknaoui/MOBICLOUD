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
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.usecase.m06_m07_repair_migration.LocalRepairBuffer
import com.mobicloud.domain.usecase.m11_join.JoinStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.mobicloud.domain.repository.SignalingRepository
import javax.inject.Inject

/**
 * Traite en temps réel les événements du protocole Bully reçus par le réseau P2P.
 *
 * Retourne un [Flow] de [Result<ElectionEvent>] afin que l'appelant (ViewModel/Service)
 * puisse réagir sémantiquement à chaque type d'événement :
 *  - [ElectionEvent.ShouldStartOwnElection] → déclencher [RunBullyElectionUseCase]
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
    private val signalingRepository: SignalingRepository,
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

    private suspend fun processPayload(payload: ElectionPayload): Result<ElectionEvent> {
        val localIdentity = securityRepository.getIdentity().getOrElse { error ->
            throw IllegalStateException("Cannot retrieve local identity to process election event", error)
        }
        val localScore = trustScoreProvider.getTrustScore(localIdentity.nodeId).toFloat()

        val skewMs = abs(System.currentTimeMillis() - payload.timestampMs)
        if (skewMs > BULLY_TIMESTAMP_WINDOW_MS) {
            return Result.failure(
                Exception("Bully payload ${payload.type.name} hors fenetre (skew=${skewMs}ms) -- ignore (replay suspect).")
            )
        }

        val verified = verifyPayloadSignature(payload)
        if (verified.isFailure) {
            return Result.failure(verified.exceptionOrNull() ?: Exception("Signature verification failed"))
        }

        return when (payload.type) {

            ElectionMessageType.ELECTION -> {
                val localSPState = joinStateMachine?.currentState?.value
                if (localSPState is NodeJoinState.SuperPair) {
                    // Bully standard : un SP qui reçoit ELECTION répond COORDINATOR pour stopper
                    // l'élection du demandeur et l'orienter vers notre cluster existant.
                    // Sans ça, un challenger sans réponse se déclare COORDINATOR → double SP.
                    val localClusterId = runCatching { nodeSettingsRepository.getSettings().clusterId }.getOrDefault("")
                    val coordinatorPayload = createPayload(localIdentity, localScore, ElectionMessageType.COORDINATOR, localClusterId)
                        .getOrElse { error -> throw Exception("Failed to sign COORDINATOR payload", error) }
                    networkClient.broadcastElectionMessage(coordinatorPayload)
                        .getOrElse { error -> throw Exception("Failed to broadcast COORDINATOR", error) }
                    Result.success(ElectionEvent.Ignored)
                } else if (electionStateManager.isInCooldown()) {
                    Result.success(ElectionEvent.Ignored)
                } else if (isHigherPriority(localScore, localIdentity.nodeId, payload.reliabilityScore, payload.senderNodeId)) {
                    val alivePayload = createPayload(localIdentity, localScore, ElectionMessageType.ALIVE)
                        .getOrElse { error ->
                            throw Exception("Failed to sign ALIVE payload", error)
                        }
                    networkClient.broadcastElectionMessage(alivePayload).getOrElse { error ->
                        throw Exception("Failed to broadcast ALIVE message", error)
                    }
                    Result.success(ElectionEvent.ShouldStartOwnElection)
                } else {
                    Result.success(ElectionEvent.Ignored)
                }
            }

            ElectionMessageType.ALIVE -> {
                Result.success(ElectionEvent.AliveReceived)
            }

            ElectionMessageType.ABDICATION -> {
                val senderPeer = peerRepository.peers.value
                    .find { it.identity.nodeId == payload.senderNodeId }
                    ?: return Result.failure(Exception("Received ABDICATION from unknown peer '${payload.senderNodeId}' -- ignoring."))

                if (!senderPeer.isSuperPair) {
                    networkEventRepository.pushEvent("WARNING: Réception d'ABDICATION depuis '${payload.senderNodeId}' qui n'est pas Super-Pair — ignoré.")
                    return Result.success(ElectionEvent.Ignored)
                }

                peerRepository.clearSuperPairStatus(payload.senderNodeId)
                Result.success(ElectionEvent.AbdicationReceived(payload.senderNodeId))
            }

            ElectionMessageType.COORDINATOR -> {
                val senderPeer = peerRepository.peers.value
                    .find { it.identity.nodeId == payload.senderNodeId }
                    ?: return Result.failure(
                        Exception("Received COORDINATOR from unknown peer '${payload.senderNodeId}' -- ignoring.")
                    )

                peerRepository.registerOrUpdatePeer(
                    identity = senderPeer.identity,
                    timestampMs = System.currentTimeMillis(),
                    isSuperPair = true
                )

                // Story 12.1 : clusterId adopté depuis le COORDINATOR (non-self, non-blank).
                // Plus de dérivation SSID WiFi — seuls COORDINATOR, JOIN_ACCEPT et BullySolo écrivent le clusterId.
                if (payload.clusterId.isNotBlank() && payload.senderNodeId != localIdentity.nodeId) {
                    nodeSettingsRepository.updateClusterId(payload.clusterId)
                }

                val pendingRequests = localRepairBuffer.drain()
                if (pendingRequests.isNotEmpty()) {
                    networkEventRepository.pushEvent(
                        "[BUFFER] ${pendingRequests.size} requête(s) de réparation drainées (FIFO) → nouveau Super-Pair ${payload.senderNodeId.take(8)}"
                    )
                }

                val localClusterId = runCatching { nodeSettingsRepository.getSettings().clusterId }.getOrDefault("")
                val differentCluster = payload.clusterId != localClusterId
                val differentSenderSameCluster =
                    payload.clusterId == localClusterId && payload.senderNodeId != localIdentity.nodeId
                if (differentCluster || differentSenderSameCluster) {
                    joinStateMachine?.transition(
                        JoinEvent.CoordinatorReceived(
                            senderNodeId = payload.senderNodeId.hexToByteArray(),
                            clusterId = payload.clusterId
                        )
                    )
                }

                Result.success(ElectionEvent.CoordinatorRegistered(payload.senderNodeId))
            }
        }
    }

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

    private suspend fun createPayload(
        identity: NodeIdentity,
        score: Float,
        type: ElectionMessageType,
        clusterId: String = ""
    ): Result<ElectionPayload> {
        val timestampMs = System.currentTimeMillis()
        val dataToSign = electionSignedBytes(type, identity.nodeId, score, clusterId, timestampMs)
        val signature = securityRepository.signData(dataToSign).getOrElse { error ->
            return Result.failure(Exception("Failed to sign ${type.name} payload", error))
        }
        return Result.success(
            ElectionPayload(
                senderNodeId = identity.nodeId,
                type = type,
                reliabilityScore = score,
                signatureBytes = signature,
                clusterId = clusterId,
                timestampMs = timestampMs
            )
        )
    }

    private suspend fun verifyPayloadSignature(payload: ElectionPayload): Result<Unit> {
        var senderPeer = peerRepository.peers.value
            .find { it.identity.nodeId == payload.senderNodeId }

        if (senderPeer == null) {
            // S11 (stabilité critique) : si le message provient d'un pair inconnu, on déclenche un
            // fetch réactif via le relais HA pour tenter de charger sa clé publique en base de données locale.
            runCatching { signalingRepository.fetchActiveSuperPeers() }
            try {
                kotlinx.coroutines.withTimeout(3000L) {
                    peerRepository.peers.first { list ->
                        val found = list.find { it.identity.nodeId == payload.senderNodeId }
                        if (found != null) {
                            senderPeer = found
                            true
                        } else {
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                // Le timeout a expiré ou une erreur est survenue, on ignore et poursuit avec le statut null
            }
        }

        val actualPeer = senderPeer ?: return Result.failure(
            Exception("Bully ${payload.type.name} from unknown peer '${payload.senderNodeId}' -- ignored.")
        )

        val dataToVerify = electionSignedBytes(
            type = payload.type,
            senderNodeId = payload.senderNodeId,
            reliabilityScore = payload.reliabilityScore,
            clusterId = payload.clusterId,
            timestampMs = payload.timestampMs
        )

        val isValid = securityRepository.verifySignature(
            data = dataToVerify,
            signature = payload.signatureBytes,
            publicKey = actualPeer.identity.publicKeyBytes
        ).getOrElse { error ->
            return Result.failure(
                Exception("Signature verification failed for ${payload.type.name} from '${payload.senderNodeId}'", error)
            )
        }

        return if (isValid) Result.success(Unit)
        else Result.failure(
            Exception("Invalid signature on ${payload.type.name} from '${payload.senderNodeId}' -- potential forgery, ignored.")
        )
    }
}
