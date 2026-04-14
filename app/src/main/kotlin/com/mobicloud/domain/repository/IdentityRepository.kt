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
}
