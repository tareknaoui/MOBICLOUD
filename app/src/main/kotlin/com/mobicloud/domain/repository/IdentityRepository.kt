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
     * Lit l'identité existante SANS jamais en générer une nouvelle.
     * Retourne `null` si aucune identité n'est encore persistée (DB et Keystore vides).
     * Contrairement à [getIdentity], n'a aucun effet de bord (pas de génération de keypair).
     */
    suspend fun peekIdentity(): Result<NodeIdentity?>

    /**
     * Met à jour le score de fiabilité persisté en Room DB.
     * N'affecte pas les clés cryptographiques.
     *
     * @param nodeId Identifiant du nœud local (clé primaire Room).
     * @param score Valeur normalisée dans [0.0, 1.0].
     */
    suspend fun updateReliabilityScore(nodeId: String, score: Float): Result<Unit>

    /**
     * Écrase l'identité locale en base avec une identité restaurée depuis un code de récupération.
     * Appelé après [SecurityRepository.importFromRecoveryCode] pour que [getIdentity] retourne
     * la bonne identité sans générer un nouveau keypair.
     */
    suspend fun restoreIdentity(identity: com.mobicloud.domain.models.NodeIdentity): Result<Unit>
}
