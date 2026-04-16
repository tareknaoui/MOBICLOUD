package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeIdentity

/**
 * Interface pour la gestion de l'identité du nœud de manière persistée.
 */
interface IdentityRepository {
    
    /**
     * Récupère l'identité du nœud.
     * Si elle n'existe pas en DB, elle doit être générée, insérée puis retournée.
     */
    suspend fun getIdentity(): Result<NodeIdentity>

    /**
     * Met à jour le score de fiabilité persisté en Room DB.
     * N'affecte pas les clés cryptographiques.
     *
     * @param nodeId Identifiant du nœud local (clé primaire Room).
     * @param score Valeur normalisée dans [0.0, 1.0].
     */
    suspend fun updateReliabilityScore(nodeId: String, score: Float): Result<Unit>
}
