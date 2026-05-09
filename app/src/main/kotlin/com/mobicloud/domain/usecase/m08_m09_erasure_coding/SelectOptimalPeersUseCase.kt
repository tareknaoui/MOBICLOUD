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
        baseK: Int = 2,
        // MODE TEST UNIQUEMENT : autorise un meme pair a recevoir plusieurs fragments
        // (round-robin) si le nombre de pairs est insuffisant pour une distribution 1:1.
        // CASSE la garantie de redondance Reed-Solomon -- si un pair a 2 fragments et meurt,
        // 2 fragments perdus, reconstruction impossible. Default false = production stricte.
        allowDuplicatePeers: Boolean = false
    ): Result<OptimalPeersResult> = runCatching {
        // baseK = 2 (au lieu de 4) pour permettre des tests avec un petit nombre de pairs
        // (3 phones suffisent : K=2 data + N=1 parite). Reed-Solomon RS(2,1) tolere la perte
        // de 1 nœud sur 3. A reaugmenter (K=4) pour des deploiements en production avec >=5 nœuds.
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

        // BUG-FIX 2026-05-09 : Bully peut ajouter le nœud LOCAL dans peerRepository (pour
        // marquer isSuperPair=true localement) avec ipAddress=null/port=null car ces champs
        // ne sont pas connus du use-case Bully. De meme, un COORDINATOR recu d'un pair distant
        // ne mettait pas a jour son IP. Resultat : des pairs sans IP/port se retrouvaient dans
        // peerRepository et SelectOptimalPeers les choisissait pour la distribution -> sendBlock
        // tentait de se connecter a "null:null" et le fragment restait coince.
        // Filtrage defensif ici : un pair sans IP ou port n'est pas distribuable, on l'exclut.
        val reachablePeers = activePeers.filter { it.ipAddress != null && it.port != null }

        // Taille approximative d'un fragment
        val fragmentSize = (fileSizeBytes + baseK - 1) / baseK

        // Filtrer selon la capacité :
        // - Les pairs avec freeStorageBytes > 0 sont filtrés normalement (Marge 100 Mo + taille fragment).
        // - Les pairs avec freeStorageBytes == 0 ont une capacité INCONNUE (anciens peers sans le champ)
        //   → on les inclut de façon optimiste pour ne pas les exclure pendant la montée de version.
        val MIN_FREE_BYTES = 100L * 1024 * 1024
        val requiredSpace = fragmentSize + MIN_FREE_BYTES

        val capablePeers = reachablePeers.filter {
            it.freeStorageBytes == 0L || it.freeStorageBytes >= requiredSpace
        }
        
        // Déféré 4 : exception typée au lieu d'IllegalStateException générique
        // En mode allowDuplicatePeers, on accepte des qu'on a >= 1 pair (round-robin compense).
        val minRequired = if (allowDuplicatePeers) 1 else baseK
        if (capablePeers.size < minRequired) {
            throw PeerSelectionException.InsufficientCapableNodes(
                required = minRequired,
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
            allowDuplicatePeers -> dynamicN // Mode test : on garde dynamicN, le round-robin compense
            else -> throw PeerSelectionException.InsufficientRedundancyNodes(
                available = availableNodes,
                baseK = baseK
            )
        }

        // Sélection finale
        val totalFragments = baseK + finalN
        val finalSelectedPeers = if (allowDuplicatePeers && sortedPeers.size < totalFragments) {
            // Mode test : round-robin sur les pairs disponibles pour produire `totalFragments` slots.
            // Les fragments seront distribues 1, 2, 3, ... vers peer[0], peer[1], peer[2], peer[0], ...
            // ATTENTION : 2 fragments sur le meme pair = perte simultanee si ce pair tombe.
            List(totalFragments) { i -> sortedPeers[i % sortedPeers.size] }
        } else {
            sortedPeers.take(totalFragments)
        }
        val finalParams = ErasureParameters(k = baseK, n = finalN)

        OptimalPeersResult(params = finalParams, selectedPeers = finalSelectedPeers)
    }
}
