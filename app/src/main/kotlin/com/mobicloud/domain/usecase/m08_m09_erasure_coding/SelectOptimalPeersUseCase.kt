package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.PeerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Erreurs métier typées retournées par [SelectOptimalPeersUseCase].
 * Permettent à l'appelant de distinguer les cas d'échec et d'afficher un message ciblé.
 */
sealed class PeerSelectionException(message: String) : Exception(message) {
    /** paramètre baseK invalide (zéro ou négatif). */
    class InvalidBaseK(baseK: Int) :
        PeerSelectionException("baseK doit être > 0, reçu : $baseK")

    /** Pas assez de nœuds capables de stocker un fragment. */
    class InsufficientCapableNodes(required: Int, available: Int) :
        PeerSelectionException(
            "Pas assez de nœuds avec suffisamment d'espace libre. " +
            "Requis : $required, Disponibles : $available"
        )

    /** Impossible d'assurer la moindre redondance (n>=1) avec les nœuds présents. */
    class InsufficientRedundancyNodes(available: Int, baseK: Int) :
        PeerSelectionException(
            "Impossible d'assurer une redondance minimale : $available nœud(s) capable(s) " +
            "disponible(s), K=$baseK requis + au moins 1 nœud de parité."
        )

    /** Le Flow de pairs n'a pas émis dans le délai imparti. */
    class PeerFlowTimeout :
        PeerSelectionException("Timeout : aucune liste de pairs reçue dans les 5 secondes.")
}

data class OptimalPeersResult(
    val params: ErasureParameters,
    val selectedPeers: List<Peer>
)

class SelectOptimalPeersUseCase @Inject constructor(
    private val peerRepository: PeerRepository
) {
    companion object {
        private const val PEER_FLOW_TIMEOUT_MS = 5_000L
    }

    suspend operator fun invoke(
        fileSizeBytes: Long,
        baseK: Int = 4
    ): Result<OptimalPeersResult> = runCatching {
        // Déféré 3 : guard baseK <= 0 → division par zéro évitée
        if (baseK <= 0) throw PeerSelectionException.InvalidBaseK(baseK)

        // Déféré 1 : timeout sur peers.first() pour ne pas bloquer indéfiniment
        val activePeers = try {
            withTimeout(PEER_FLOW_TIMEOUT_MS) {
                peerRepository.peers.first()
            }.filter { it.isActive }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw PeerSelectionException.PeerFlowTimeout()
        }
        
        // Taille approximative d'un fragment
        val fragmentSize = (fileSizeBytes + baseK - 1) / baseK
        
        // Filtrer selon la capacité :
        // - Les pairs avec freeStorageBytes > 0 sont filtrés normalement (Marge 100 Mo + taille fragment).
        // - Les pairs avec freeStorageBytes == 0 ont une capacité INCONNUE (anciens peers sans le champ)
        //   → on les inclut de façon optimiste pour ne pas les exclure pendant la montée de version.
        val MIN_FREE_BYTES = 100L * 1024 * 1024
        val requiredSpace = fragmentSize + MIN_FREE_BYTES

        val capablePeers = activePeers.filter {
            it.freeStorageBytes == 0L || it.freeStorageBytes >= requiredSpace
        }
        
        // Déféré 4 : exception typée au lieu d'IllegalStateException générique
        if (capablePeers.size < baseK) {
            throw PeerSelectionException.InsufficientCapableNodes(
                required = baseK,
                available = capablePeers.size
            )
        }
        
        // Trier par score de fiabilité décroissant
        val sortedPeers = capablePeers.sortedByDescending { it.identity.reliabilityScore }
        
        // Évaluer le Maillon Faible sur le Pool de Sélection de base (les K meilleurs nœuds)
        val coreSelection = sortedPeers.take(baseK)
        val weakestLinkScore = coreSelection.minOf { it.identity.reliabilityScore }
        
        // Déterminer la redondance dynamique M (n)
        val dynamicN = when {
            weakestLinkScore >= 0.8f -> 1 // Réseau très stable
            weakestLinkScore >= 0.5f -> 2 // Réseau normal
            else -> 4 // Réseau instable, on maximise la redondance
        }
        
        // Ajuster dynamiquement N s'il n'y a pas assez de téléphones disponibles dans tout le réseau
        val totalRequiredNodes = baseK + dynamicN
        val availableNodes = sortedPeers.size
        
        val finalN = when {
            availableNodes >= totalRequiredNodes -> dynamicN
            availableNodes > baseK -> availableNodes - baseK // Réduire la redondance au maximum physiquement possible
            else -> throw PeerSelectionException.InsufficientRedundancyNodes(
                available = availableNodes,
                baseK = baseK
            )
        }
        
        // Sélection finale
        val finalSelectedPeers = sortedPeers.take(baseK + finalN)
        val finalParams = ErasureParameters(k = baseK, n = finalN)
        
        OptimalPeersResult(params = finalParams, selectedPeers = finalSelectedPeers)
    }
}
