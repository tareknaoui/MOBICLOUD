package com.mobicloud.domain.repository

import com.mobicloud.domain.models.NodeIdentity

/**
 * Interface pour la gestion de l'identité et de la sécurité du noeud,
 * isolant la couche domaine des implémentations AndroidKeyStore ou Software.
 */
interface SecurityRepository {
    
    /**
     * Récupère l'identité existante du noeud.
     * Doit retourner une erreur si aucune identité n'a encore été générée.
     */
    suspend fun getIdentity(): Result<NodeIdentity>
    
    /**
     * Génère une nouvelle paire de clés (Prive/Publique) et retourne la nouvelle identité.
     * En cas d'échec matériel (Hardware Keystore indisponible), effectue un fallback 
     * sur un chiffrement logiciel sécurisé.
     */
    suspend fun generateIdentity(): Result<NodeIdentity>

    /**
     * Signe les données passées en paramètre avec la clé privée du nœud.
     * 
     * @param data Le payload binaire à signer.
     * @return La signature au format ByteArray, ou une erreur si l'identité n'est pas encore générée.
     */
    suspend fun signData(data: ByteArray): Result<ByteArray>

    /**
     * Vérifie une signature avec la clé publique fournie.
     *
     * @param data       Le payload binaire original.
     * @param signature  La signature à vérifier.
     * @param publicKey  La clé publique de l'émetteur (ByteArray brut).
     * @return `true` si la signature est valide, `false` sinon.
     */
    suspend fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Result<Boolean>
}
