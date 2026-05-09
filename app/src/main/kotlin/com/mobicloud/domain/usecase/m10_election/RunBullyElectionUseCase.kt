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
 * @param defaultDispatcher Dispatcher injecté pour la testabilité (production = [Dispatchers.Default]).
 */
class RunBullyElectionUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val securityRepository: SecurityRepository,
    private val trustScoreProvider: ITrustScoreProvider,
    private val networkClient: IElectionNetworkClient,
    private val electionStateManager: ElectionStateManager,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        // Fenêtre de stabilisation Bully : la condition d'élection (aucun super-peer +
        // au moins un autre pair actif) doit tenir 20s d'affilée pour éviter les courses
        // de bootstrap où plusieurs nœuds s'élisent en parallèle avant échange complet
        // des heartbeats/peer-list. Laisse 3-4 cycles GET_PEERS pour que la registry
        // se peuple. Configurable via constante pour les tests (qui peuvent avancer
        // l'horloge virtuelle sans attendre 20s réelles).
        const val MONITORING_WINDOW_MS = 20_000L
    }

    operator fun invoke(): Flow<Result<SuperPairElection>> = flow {

        // Étape 1 (AC1) : Monitoring réactif avec deux garde-fous bootstrap.
        //
        // Bug historique : si N nœuds démarraient simultanément (ex. 4 phones lancés ensemble),
        // chacun voyait `peerRepository.peers` vide ou non-encore-peuplé, déclenchait Bully en
        // parallèle, et "gagnait" tous car les ELECTION étaient rejetées par les voisins (sender
        // pas encore dans leur PeerRegistry, signature non vérifiable). Résultat : N super-peers.
        //
        // Garde-fou 1 : on n'élit pas tant qu'on n'a pas découvert AU MOINS UN autre pair actif.
        //               Si on est vraiment seul, attendre — un pair finira par apparaître ou bien
        //               on est isolé légitimement (cluster solo, OK de devenir super-peer).
        //               Pour distinguer "seul vraiment" vs "seul temporairement (bootstrap)",
        //               on combine avec le délai du garde-fou 2.
        //
        // Garde-fou 2 : la condition (pas de super-peer ET au moins un autre pair) doit être
        //               stable pendant 20s consécutives. Laisse 3-4 cycles GET_PEERS pour que la
        //               registry se peuple, et la signature des pairs distants soit échangée.
        if (electionStateManager.isInCooldown()) {
            emit(Result.failure(Exception("Election aborted: Node is in cooldown.")))
            return@flow
        }

        val localIdentity = securityRepository.getIdentity().getOrElse { error ->
            emit(Result.failure(error))
            return@flow
        }

        peerRepository.peers
            .map { peers ->
                val noSuperPeer = peers.none { it.isActive && it.isSuperPair }
                // Garde-fou bootstrap : on a deja decouvert AU MOINS UN autre pair dans le
                // registre (peu importe son etat isActive courant). On ne verifie PAS isActive
                // ici car la boucle d'eviction (15s) flicker les pairs entre actif/inactif vs
                // GET_PEERS (30s), ce qui ferait flop la condition et annulerait le timer
                // de monitoring en boucle. La presence dans le registre suffit a prouver qu'on
                // n'est pas en bootstrap solo.
                val hasOtherKnownPeer = peers.any { it.identity.nodeId != localIdentity.nodeId }
                noSuperPeer && hasOtherKnownPeer
            }
            // CRITIQUE : distinctUntilChanged() ESSENTIEL avant transformLatest.
            // Sans ca, peerRepository.peers (StateFlow alimente par DAO) re-emet a chaque
            // GET_PEERS (10s) / JOIN (30s) / eviction (1s) -- meme si shouldElect reste true,
            // transformLatest annule le delay 20s et le redemarre, et les 20s consecutives
            // ne s'accumulent JAMAIS -> Bully ne fire jamais -> aucun super-peer elu.
            .distinctUntilChanged()
            .transformLatest { shouldElect ->
                if (shouldElect) {
                    delay(MONITORING_WINDOW_MS)
                    emit(Unit)
                }
                // Si la condition redevient false (super-peer apparu OU plus de pair actif),
                // transformLatest annule le delay en cours -> timer reset.
            }
            .firstOrNull()

        // Re-vérification de sécurité après la fenêtre de monitoring
        val activeSuperPair = peerRepository.peers.value.any { it.isActive && it.isSuperPair }
        if (activeSuperPair) {
            emit(Result.failure(Exception("Election aborted: An active Super-Pair appeared during the monitoring window.")))
            return@flow
        }
        val localScore = trustScoreProvider.getTrustScore(localIdentity.nodeId).toFloat()
        val localClusterId = nodeSettingsRepository.getSettings().clusterId

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
            val coordinatorPayload = createPayload(localIdentity, localScore, ElectionMessageType.COORDINATOR, localClusterId)
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
