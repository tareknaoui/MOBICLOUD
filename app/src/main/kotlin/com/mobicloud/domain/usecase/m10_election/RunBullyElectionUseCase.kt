package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.SuperPairElection
import com.mobicloud.domain.models.electionSignedBytes
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m11_join.MarkSelfAsSuperPairUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * UseCase implémentant le protocole d'élection Bully.
 *
 * Surveille de manière réactive l'absence de Super-Pair pendant 5 secondes
 * consécutives avant de déclencher le protocole (AC1 — monitoring réactif via StateFlow).
 *
 * Story 12.1 : [locationRepository] supprimé — le COORDINATOR ne transporte plus de GPS.
 * Le clusterId est attribué par JOIN_ACCEPT ou BullySolo (getClusterIdOnce()).
 */
class RunBullyElectionUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val securityRepository: SecurityRepository,
    private val trustScoreProvider: ITrustScoreProvider,
    private val networkClient: IElectionNetworkClient,
    private val electionStateManager: ElectionStateManager,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val markSelfAsSuperPairUseCase: MarkSelfAsSuperPairUseCase,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        const val MONITORING_WINDOW_MS = 20_000L
        // P15 (incident 2026-05-17) : fallback isolation totale. Si après ce délai aucun peer
        // ni SP n'a été découvert (ex. 3ème device sur subnet WiFi distinct + relay filtre les
        // SPs au cluster plein), on considère le nœud "isolé légitimement" comme prévu par le
        // commentaire original du garde-fou hasOtherKnownPeer, et on déclenche BullySolo via
        // l'élection. Évite le blocage indéfini en état Undiscovered.
        const val SOLO_BOOTSTRAP_TIMEOUT_MS = 45_000L
    }

    operator fun invoke(): Flow<Result<SuperPairElection>> = flow {

        if (electionStateManager.isInCooldown()) {
            emit(Result.failure(Exception("Election aborted: Node is in cooldown.")))
            return@flow
        }

        val localIdentity = securityRepository.getIdentity().getOrElse { error ->
            emit(Result.failure(error))
            return@flow
        }

        // Race entre deux conditions de déclenchement :
        // 1. Bully classique : noSuperPeer + hasOtherKnownPeer stables pendant MONITORING_WINDOW_MS
        // 2. Solo bootstrap : aucun peer ni SP après SOLO_BOOTSTRAP_TIMEOUT_MS — fallback isolation totale
        val triggerReason = withTimeoutOrNull(SOLO_BOOTSTRAP_TIMEOUT_MS) {
            peerRepository.peers
                .map { peers ->
                    val noSuperPeer = peers.none { it.isActive && it.isSuperPair }
                    val hasOtherKnownPeer = peers.any { it.identity.nodeId != localIdentity.nodeId }
                    noSuperPeer && hasOtherKnownPeer
                }
                .distinctUntilChanged()
                .transformLatest { shouldElect ->
                    if (shouldElect) {
                        delay(MONITORING_WINDOW_MS)
                        emit(Unit)
                    }
                }
                .firstOrNull()
        }
        // Si null (timeout) : on est isolé. On vérifie qu'aucun SP n'est apparu et on procède.
        if (triggerReason == null && peerRepository.peers.value.any { it.isActive && it.isSuperPair }) {
            emit(Result.failure(Exception("Election aborted: SP appeared during solo bootstrap wait.")))
            return@flow
        }

        val activeSuperPair = peerRepository.peers.value.any { it.isActive && it.isSuperPair }
        if (activeSuperPair) {
            emit(Result.failure(Exception("Election aborted: An active Super-Pair appeared during the monitoring window.")))
            return@flow
        }
        val localScore = trustScoreProvider.getTrustScore(localIdentity.nodeId).toFloat()

        // Story 12.1 : clusterId attribué par JOIN_ACCEPT ou BullySolo — plus dérivé du SSID.
        // Si vide (première élection, jamais rejoint de cluster), génère un UUID pour créer
        // un nouveau cluster. Sans ça, MarkSelfAsSuperPairUseCase retourne prématurément
        // et la FSM reste en Undiscovered → résolution de conflit SP impossible.
        val localClusterId = nodeSettingsRepository.getClusterIdOnce()
            .ifBlank { java.util.UUID.randomUUID().toString() }

        val electionPayload = createPayload(localIdentity, localScore, ElectionMessageType.ELECTION)
            .getOrElse { error ->
                emit(Result.failure(error))
                return@flow
            }
        networkClient.broadcastElectionMessage(electionPayload).getOrElse { error ->
            emit(Result.failure(Exception("Failed to broadcast ELECTION message", error)))
            return@flow
        }

        val timeoutMillis = 3_000L
        val higherAliveReceived = withTimeoutOrNull(timeoutMillis) {
            networkClient.incomingMessages.firstOrNull { msg ->
                msg.type == ElectionMessageType.ALIVE && isHigherPriority(
                    otherScore = msg.reliabilityScore,
                    otherId = msg.senderNodeId,
                    localScore = localScore,
                    localId = localIdentity.nodeId
                )
            }
        }

        if (higherAliveReceived != null) {
            emit(Result.failure(Exception("Election lost to a higher scoring node: ${higherAliveReceived.senderNodeId}")))
        } else {
            val coordinatorPayload = createPayload(localIdentity, localScore, ElectionMessageType.COORDINATOR, localClusterId)
                .getOrElse { error ->
                    emit(Result.failure(error))
                    return@flow
                }

            networkClient.broadcastElectionMessage(coordinatorPayload).getOrElse { error ->
                emit(Result.failure(Exception("Failed to broadcast COORDINATOR message", error)))
                return@flow
            }

            peerRepository.registerOrUpdatePeer(
                identity = localIdentity,
                timestampMs = System.currentTimeMillis(),
                isSuperPair = true
            )

            markSelfAsSuperPairUseCase.invoke(localClusterId)

            emit(Result.success(SuperPairElection(localIdentity)))
        }

    }.flowOn(defaultDispatcher)

    private fun isHigherPriority(
        otherScore: Float,
        otherId: String,
        localScore: Float,
        localId: String
    ): Boolean {
        if (otherScore > localScore) return true
        if (otherScore < localScore) return false
        return otherId > localId
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
            return Result.failure(Exception("Failed to sign election payload of type ${type.name}", error))
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
}
