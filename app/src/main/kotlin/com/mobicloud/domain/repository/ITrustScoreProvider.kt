package com.mobicloud.domain.repository

/**
 * Fournit le Score de Fiabilité (Trust Score - SF) pour un nœud donné.
 * Ce score est utilisé pour déterminer la priorité lors de l'élection d'un Super-Pair.
 */
interface ITrustScoreProvider {
    /**
     * Renvoie le score de fiabilité pour un identifiant de nœud spécifique.
     * 
     * @param publicId L'identifiant public du nœud.
     * @return Le score entier (plus il est grand, plus le nœud est fiable).
     */
    suspend fun getTrustScore(publicId: String): Int
}
