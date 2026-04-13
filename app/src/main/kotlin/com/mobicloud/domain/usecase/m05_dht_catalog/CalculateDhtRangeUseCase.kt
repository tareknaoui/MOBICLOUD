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
        // Pr\u00e9condition critique (BH-06) : la comparaison lexicographique sur String n'est correcte
        // que si tous les identifiants ont exactement la m\u00eame longueur (ex: SHA-256 hex = 64 chars).
        // Un ID tronqu\u00e9 ou de format diff\u00e9rent produirait silencieusement des r\u00e9sultats erron\u00e9s
        // sur l'anneau DHT (ex: "ff" > "100" en lexico alors que 255 < 256 en d\u00e9cimal).
        require(key.length == nodeId.length && nodeId.length == successorId.length) {
            "Tous les IDs DHT doivent avoir la m\u00eame longueur pour garantir la validit\u00e9 " +
            "de la comparaison lexicographique. Got: key=${key.length}, nodeId=${nodeId.length}, " +
            "successorId=${successorId.length}"
        }

        if (nodeId == successorId) {
            // Nous sommes le seul n\u0153ud sur l'anneau, nous couvrons tout
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
