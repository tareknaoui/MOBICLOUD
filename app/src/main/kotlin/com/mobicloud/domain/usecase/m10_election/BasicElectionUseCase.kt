package com.mobicloud.domain.usecase.m10_election

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.SuperPairElection
import com.mobicloud.domain.repository.ITrustScoreProvider
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * UseCase gérant l'élection basique du coordinateur (Super-Pair) d'un sous-réseau.
 *
 * Règles :
 * 1. Poids d'élection primaire : le Score de Fiabilité (SF) du nœud.
 * 2. Départage déterministe (Bris d'égalité) : basé sur l'ordre lexicographique des identifiants
 *    publics [NodeIdentity.nodeId].
 * 3. Si le nœud local est élu, un Genesis Hashcash est généré comme preuve inaugurale.
 */
class BasicElectionUseCase @Inject constructor(
    private val trustScoreProvider: ITrustScoreProvider,
    private val securityRepository: SecurityRepository
) {
    /**
     * @param peers Liste courante des pairs enregistrés.
     * @return Flow contenant le résultat [SuperPairElection] ou une [Exception].
     */
    operator fun invoke(peers: List<Peer>): Flow<Result<SuperPairElection>> = flow {
        try {
            val result = coroutineScope {
                val localIdentity = securityRepository.getIdentity()
                    .getOrElse { return@coroutineScope Result.failure(it) }

                // Constituer la liste de toutes les entités de l'élection (sans doublon)
                val allNodes = (peers.map { it.identity } + localIdentity).distinctBy { it.nodeId }

                // Évaluer le SF pour chaque candidat de façon concurrente
                val scoreMap = allNodes.map { node ->
                    async {
                        val score = trustScoreProvider.getTrustScore(node.nodeId)
                        node to score
                    }
                }.awaitAll()

                val maxScore = scoreMap.maxOfOrNull { it.second } 
                    ?: return@coroutineScope Result.success(SuperPairElection(localIdentity))

                val topCandidates = scoreMap.filter { it.second == maxScore }.map { it.first }

                val expectedLength = localIdentity.nodeId.length
                val validCandidates = topCandidates.filter { it.nodeId.length == expectedLength }

                val electedNode = validCandidates.maxByOrNull { it.nodeId } ?: localIdentity
                Result.success(SuperPairElection(electedNode))
            }
            emit(result)
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.Default)
}
