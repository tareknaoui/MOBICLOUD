package com.mobicloud.data.election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.repository.IElectionNetworkClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub in-process de IElectionNetworkClient.
 *
 * Simule un réseau P2P multi-nœuds :
 * - ELECTION  → émet ALIVE depuis chaque pair simulé dont la priorité est supérieure
 *               au nœud émetteur (logique Bully standard). Un nœud sans concurrent
 *               de score plus élevé gagne donc l'élection.
 * - COORDINATOR → réinjecte le payload sur [incomingMessages] pour que
 *                 [ProcessIncomingElectionEventUseCase] puisse enregistrer le coordinateur.
 * - ALIVE     → pas de rebouclage (géré en interne par RunBullyElectionUseCase).
 *
 * Pour les tests et démos, appeler [addSimulatedPeer] avant de déclencher une élection.
 * Remplacé dans une Story future par le vrai transport UDP/Multicast.
 */
@Singleton
class StubElectionNetworkClient @Inject constructor() : IElectionNetworkClient {

    private val _incomingMessages = MutableSharedFlow<ElectionPayload>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<ElectionPayload> = _incomingMessages

    // Paires simulées (nodeId, reliabilityScore) — thread-safe pour les tests concurrents
    private val simulatedPeers = CopyOnWriteArrayList<Pair<String, Float>>()

    /** Ajoute un pair virtuel qui participera aux prochaines élections. */
    fun addSimulatedPeer(nodeId: String, score: Float) {
        simulatedPeers.add(nodeId to score)
    }

    /** Supprime tous les pairs simulés. */
    fun clearSimulatedPeers() {
        simulatedPeers.clear()
    }

    override suspend fun broadcastElectionMessage(payload: ElectionPayload): Result<Unit> {
        when (payload.type) {
            ElectionMessageType.ELECTION -> {
                // Chaque pair simulé de priorité supérieure répond ALIVE (protocole Bully)
                simulatedPeers.forEach { (peerId, peerScore) ->
                    if (isHigherPriority(peerScore, peerId, payload.reliabilityScore, payload.senderNodeId)) {
                        _incomingMessages.emit(
                            ElectionPayload(
                                senderNodeId   = peerId,
                                type           = ElectionMessageType.ALIVE,
                                reliabilityScore = peerScore,
                                signatureBytes = ByteArray(0)
                            )
                        )
                    }
                }
            }
            ElectionMessageType.COORDINATOR -> {
                // Reboucler pour que ProcessIncomingElectionEventUseCase enregistre le coordinateur
                _incomingMessages.emit(payload)
            }
            else -> Unit // ALIVE / ABDICATION : pas de rebouclage depuis l'émetteur
        }
        return Result.success(Unit)
    }

    /**
     * Retourne `true` si le pair distant (other) est prioritaire sur le nœud local (local).
     * Bris d'égalité : comparaison lexicographique des nodeId.
     */
    private fun isHigherPriority(
        otherScore: Float,
        otherId:    String,
        localScore: Float,
        localId:    String
    ): Boolean = when {
        otherScore > localScore -> true
        otherScore < localScore -> false
        else                    -> otherId > localId
    }
}
