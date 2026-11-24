package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.electionSignedBytes
import com.mobicloud.domain.repository.IElectionNetworkClient
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conflit de Super-Pairs du même cluster détecté via l'annuaire du relais (split-brain).
 *
 * L'annuaire du relais n'est ni signé ni porteur du numéro de mandat : il ne doit donc
 * pas décider qui cède. Avant, le Super-Pair de plus petit nodeId abdiquait aussitôt, ce
 * qui pouvait faire abdiquer le titulaire du mandat le plus récent au profit d'un
 * Super-Pair périmé.
 *
 * Désormais l'annuaire sert seulement de **détecteur** : le Super-Pair local réaffirme son
 * mandat en diffusant son COORDINATOR signé (term inclus). Chaque Super-Pair qui le reçoit
 * applique la règle de [ProcessIncomingElectionEventUseCase] — ordre lexicographique sur
 * (term, score, nodeId) — : le perdant abdique (StepDownRequired), le gagnant réaffirme.
 * Les deux côtés appliquent le même ordre total, donc un seul Super-Pair subsiste.
 *
 * Sans mandat local (term = 0, ancienne version), l'ancienne règle par nodeId s'applique.
 */
@Singleton
class ResolveRelaySuperPeerConflictUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val trustScoreProvider: ITrustScoreProvider,
    private val electionStateManager: ElectionStateManager,
    private val networkClient: IElectionNetworkClient,
    private val networkEventRepository: NetworkEventRepository
) {
    enum class Decision {
        /** Mandat réaffirmé par COORDINATOR signé : le départage se fait par les terms. */
        MANDATE_ASSERTED,
        /** Pas de mandat numéroté localement et rival prioritaire par nodeId : abdiquer. */
        YIELD,
        /** Pas de mandat numéroté localement et nodeId local prioritaire : garder le rôle. */
        KEEP
    }

    /** Source de temps, remplaçable en test. */
    internal var clockMs: () -> Long = System::currentTimeMillis

    private val lastAssertMs = HashMap<String, Long>()

    /**
     * @param clusterId cluster commun au Super-Pair local et au rival.
     * @param rivalNodeId nodeId du Super-Pair rival annoncé par le relais.
     * @param localNodeId nodeId local, pour la règle de repli sans mandat.
     */
    suspend operator fun invoke(clusterId: String, rivalNodeId: String, localNodeId: String): Decision {
        val myTerm = electionStateManager.ownTerm(clusterId)
        if (myTerm <= 0L) {
            return if (rivalNodeId > localNodeId) Decision.YIELD else Decision.KEEP
        }

        val now = clockMs()
        val due = synchronized(lastAssertMs) {
            val last = lastAssertMs[rivalNodeId]
            if (last != null && now - last < REASSERT_INTERVAL_MS) {
                false
            } else {
                lastAssertMs[rivalNodeId] = now
                true
            }
        }
        if (due) assertMandate(clusterId, myTerm, rivalNodeId)
        return Decision.MANDATE_ASSERTED
    }

    private suspend fun assertMandate(clusterId: String, term: Long, rivalNodeId: String) {
        val sent = runCatching {
            val identity = securityRepository.getIdentity().getOrThrow()
            val score = trustScoreProvider.getTrustScore(identity.nodeId).toFloat()
            val timestampMs = System.currentTimeMillis()
            val signature = securityRepository.signData(
                electionSignedBytes(ElectionMessageType.COORDINATOR, identity.nodeId, score, clusterId, timestampMs, term)
            ).getOrThrow()
            networkClient.broadcastElectionMessage(
                ElectionPayload(
                    senderNodeId = identity.nodeId,
                    type = ElectionMessageType.COORDINATOR,
                    reliabilityScore = score,
                    signatureBytes = signature,
                    clusterId = clusterId,
                    timestampMs = timestampMs,
                    term = term
                )
            ).getOrThrow()
        }
        if (sent.isFailure) {
            // Nouvelle tentative au prochain signalement du conflit.
            synchronized(lastAssertMs) { lastAssertMs.remove(rivalNodeId) }
        }
        networkEventRepository.pushEvent(
            "[ELECTION] SP concurrent ${rivalNodeId.take(8)} signalé par le relais — " +
                if (sent.isSuccess) "mandat réaffirmé (term $term), départage par term"
                else "réaffirmation échouée (${sent.exceptionOrNull()?.message}), nouvel essai"
        )
    }

    companion object {
        /** Intervalle minimal entre deux réaffirmations face au même rival. */
        const val REASSERT_INTERVAL_MS = 5_000L
    }
}
