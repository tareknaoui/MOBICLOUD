package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.SuperPairElection
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 * @param defaultDispatcher Dispatcher injecté pour la testabilité (production = [Dispatchers.Default]).
 */
class RunBullyElectionUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val securityRepository: SecurityRepository,
    private val trustScoreProvider: ITrustScoreProvider,
    private val networkClient: IElectionNetworkClient,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    operator fun invoke(): Flow<Result<SuperPairElection>> = flow {

        // Étape 1 (AC1) : Monitoring réactif de l'absence de Super-Pair
        // On observe le StateFlow des pairs et on attend que la condition
        // "aucun Super-Pair actif" persiste pendant exactement 5 secondes CONSÉCUTIVES.
        // Si un Super-Pair réapparaît pendant la fenêtre, le timer est réinitialisé.
        peerRepository.peers
            .map { peers -> peers.none { it.isActive && it.isSuperPair } }
            .transformLatest { hasNoSuperPair ->
                if (hasNoSuperPair) {
                    delay(5_000L)
                    emit(Unit)
                }
                // Si hasNoSuperPair = false, rien n'est émis et le delay est annulé via transformLatest
            }
            .firstOrNull() // Attend la première fois que 5s s'écoulent sans Super-Pair

        // Re-vérification de sécurité après la fenêtre de monitoring
        val activeSuperPair = peerRepository.peers.value.any { it.isActive && it.isSuperPair }
        if (activeSuperPair) {
            emit(Result.failure(Exception("Election aborted: An active Super-Pair appeared during the monitoring window.")))
            return@flow
        }

        val localIdentity = securityRepository.getIdentity().getOrElse { error ->
            emit(Result.failure(error))
            return@flow
        }
        val localScore = trustScoreProvider.getTrustScore(localIdentity.nodeId).toFloat()

        // Étape 2 : Broadcast de ELECTION aux pairs actifs
        val electionPayload = createPayload(localIdentity, localScore, ElectionMessageType.ELECTION)
            .getOrElse { error ->
                emit(Result.failure(error))
                return@flow
            }
        // F-01 : vérifier le résultat du broadcast ELECTION
        networkClient.broadcastElectionMessage(electionPayload).getOrElse { error ->
            emit(Result.failure(Exception("Failed to broadcast ELECTION message", error)))
            return@flow
        }

        // Étape 3 : Attendre jusqu'à 3s un message ALIVE venant d'un pair prioritaire
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
            // Abandon — un pair de score supérieur a répondu et prend le relais
            emit(Result.failure(Exception("Election lost to a higher scoring node: ${higherAliveReceived.senderNodeId}")))
        } else {
            // Victoire — aucune réponse ALIVE prioritaire reçue dans la fenêtre
            val coordinatorPayload = createPayload(localIdentity, localScore, ElectionMessageType.COORDINATOR)
                .getOrElse { error ->
                    emit(Result.failure(error))
                    return@flow
                }

            // Étape 4 : Broadcast COORDINATOR
            // F-01 : vérifier le résultat du broadcast COORDINATOR
            networkClient.broadcastElectionMessage(coordinatorPayload).getOrElse { error ->
                emit(Result.failure(Exception("Failed to broadcast COORDINATOR message", error)))
                return@flow
            }

            // Mise à jour locale : se déclarer Super-Pair dans la registry
            peerRepository.registerOrUpdatePeer(
                identity = localIdentity,
                timestampMs = System.currentTimeMillis(),
                isSuperPair = true
            )

            emit(Result.success(SuperPairElection(localIdentity)))
        }

    }.flowOn(defaultDispatcher)

    /**
     * Compare le score de fiabilité et effectue un bris d'égalité lexicographique.
     * Retourne `true` si le pair distant EST prioritaire sur le nœud local.
     */
    private fun isHigherPriority(
        otherScore: Float,
        otherId: String,
        localScore: Float,
        localId: String
    ): Boolean {
        if (otherScore > localScore) return true
        if (otherScore < localScore) return false
        // Bris d'égalité lexicographique : l'ID le plus grand l'emporte
        return otherId > localId
    }

    /**
     * Crée et signe un payload d'élection.
     * F-02 : retourne un [Result] explicite si [SecurityRepository.signData] échoue —
     * un payload non signé ne sera jamais broadcasté.
     */
    private suspend fun createPayload(
        identity: NodeIdentity,
        score: Float,
        type: ElectionMessageType
    ): Result<ElectionPayload> {
        val dataToSign = "${identity.nodeId}:${type.name}".toByteArray()
        val signature = securityRepository.signData(dataToSign).getOrElse { error ->
            return Result.failure(Exception("Failed to sign election payload of type ${type.name}", error))
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
