package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.repository.SignalingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 9.3 — Sélection d'un Super-Pair distant (cluster ≠ local) pouvant héberger
 * un fragment lorsque le cluster local est saturé / sous-peuplé.
 *
 * Lit le snapshot mémoire SignalingRepository.latestPeers (alimenté par GET_PEERS,
 * TTL serveur 60s). Aucun appel réseau, aucune persistance — décision 100% locale.
 *
 * Le transport est délégué à BlockSenderWithRelay (Story 8.3, try-direct-then-relay).
 */
@Singleton
class RequestInterClusterHostingUseCase @Inject constructor(
    private val signalingRepository: SignalingRepository
) {
    /**
     * Retourne le meilleur Super-Pair distant capable d'héberger `blockSize` octets.
     *
     * `localClusterId` est passé par l'appelant (lu UNE FOIS en début de
     * `distribute()` — guardrail spec 9.3 Subtask 4.2 : éviter une lecture DB par fragment).
     *
     * Retourne `null` si :
     *  - `blockSize <= 0` (rien à placer)
     *  - `localClusterId` est blank (le pair local n'a pas encore de cluster)
     *  - aucun candidat ne satisfait les invariants : isSuperPair, clusterId non vide,
     *    clusterId ≠ local, freeBytes ≥ blockSize, ip non vide ET port > 0
     *
     * Tri : freeBytes décroissant (capacité maximale en tête).
     */
    fun selectRemoteHost(blockSize: Int, localClusterId: String): RelayPeer? =
        selectRemoteHosts(blockSize.toLong(), localClusterId).firstOrNull()

    /**
     * Retourne TOUS les Super-Pairs distants éligibles, triés par freeBytes décroissant.
     * Permet à l'appelant de **retry sur un 2nd, 3e candidat** quand le 1er ne répond pas.
     *
     * `excludeNodeIds` : nodeIds à écarter (ex. tentatives déjà échouées dans la même
     * passe de distribution).
     */
    fun selectRemoteHosts(
        blockSize: Long,
        localClusterId: String,
        excludeNodeIds: Set<String> = emptySet()
    ): List<RelayPeer> {
        if (blockSize <= 0L) return emptyList()
        if (localClusterId.isBlank()) return emptyList()
        return signalingRepository.latestPeers.value
            .asSequence()
            .filter { it.isSuperPair }
            .filter { it.clusterId.isNotBlank() }
            .filter { it.clusterId != localClusterId }
            .filter { it.freeBytes >= blockSize }
            .filter { it.ip.isNotBlank() && it.port > 0 }
            .filter { it.nodeId !in excludeNodeIds }
            .sortedByDescending { it.freeBytes }
            .toList()
    }
}
