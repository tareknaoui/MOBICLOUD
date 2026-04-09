package com.mobicloud.domain.usecase.m05_dht_catalog

/**
 * Use Case responsable de déterminer la couverture d'une clé (ex: hash de fichier)
 * sur l'anneau DHT (Distributed Hash Table).
 *
 * La plage de responsabilité d'un nœud P2P est le segment : [nodeId, successorId)
 * L'anneau reboucle sur lui-même, donc si successorId < nodeId, la plage traverse le point zéro de l'anneau.
 */
class CalculateDhtRangeUseCase {
    
    /**
     * @param key L'identifiant (hash) de la ressource ou fiche catalogue.
     * @param nodeId L'ID de notre nœud P2P.
     * @param successorId L'ID du nœud P2P suivant sur l'anneau (le successeur).
     * @return `true` si la `key` tombe sous la juridiction de ce `nodeId`.
     */
    operator fun invoke(key: String, nodeId: String, successorId: String): Boolean {
        if (nodeId == successorId) {
            // Nous sommes le seul nœud sur l'anneau, nous couvrons tout
            return true
        }

        return if (nodeId < successorId) {
            // Plage normale continue: [nodeId, successorId)
            key >= nodeId && key < successorId
        } else {
            // Plage coupée par le point de rebouclage: [nodeId, MAX] U [0, successorId)
            key >= nodeId || key < successorId
        }
    }
}
