package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.SuperPairElection
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase gérant l'élection basique du coordinateur (Super-Pair) d'un sous-réseau.
 *
 * Règles :
 * 1. Poids d'élection primaire : le Score de Fiabilité (SF) du nœud.
 * 2. Départage déterministe (Bris d'égalité) : basé sur l'ordre lexicographique des identifiants
 *    publics [NodeIdentity.publicId].
 * 3. Si le nœud local est élu, un Genesis Hashcash est généré comme preuve inaugurale.
 */
class BasicElectionUseCase @Inject constructor(
    private val trustScoreProvider: ITrustScoreProvider,
    private val securityRepository: SecurityRepository
) {
    /**
     * @param peers Liste courante des pairs enregistrés.
     * @return Résultat contenant [SuperPairElection] ou une [Exception].
     */
    suspend operator fun invoke(peers: List<Peer>): Result<SuperPairElection> = withContext(Dispatchers.Default) {
        try {
            val localIdentity = securityRepository.getIdentity()
                .getOrElse { return@withContext Result.failure(it) }

            // Constituer la liste de toutes les entités de l'élection (incluant soi-même)
            val allNodes = peers.map { it.identity } + localIdentity

            // Évaluer le SF pour chaque candidat de façon concurrente
            val scoreMap = allNodes.map { node ->
                async {
                    val score = trustScoreProvider.getTrustScore(node.publicId)
                    node to score
                }
            }.awaitAll()

            // Déterminer le Super-Pair avec les règles de la Story 2.2
            val electedNode = scoreMap.maxWithOrNull(Comparator { o1, o2 ->
                val node1 = o1.first
                val score1 = o1.second
                val node2 = o2.first
                val score2 = o2.second

                if (score1 != score2) {
                    score1.compareTo(score2)
                } else {
                    // BH-06 Fix: S'assurer que les chaînes ont la même longueur pour
                    // la comparaison lexicographique déterministe des clés.
                    require(node1.publicId.length == node2.publicId.length) {
                        "Pour un bris d'égalité équitable, les IDs doivent avoir la même longueur. Recu: ${node1.publicId.length} != ${node2.publicId.length}"
                    }
                    node1.publicId.compareTo(node2.publicId)
                }
            })?.first ?: localIdentity // Cas dégénéré (liste vide) -> moi-même

            Result.success(SuperPairElection(electedNode))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
