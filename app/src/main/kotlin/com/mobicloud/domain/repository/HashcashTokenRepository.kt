package com.mobicloud.domain.repository

import com.mobicloud.domain.models.HashcashToken

/**
 * Interface pour la persistance locale de la preuve de travail anti-Sybil.
 * Cela permet d'éviter de recalculer le token à chaque reconnexion.
 */
interface HashcashTokenRepository {

    /**
     * Sauvegarde la preuve de travail générée.
     */
    suspend fun saveToken(token: HashcashToken): Result<Unit>

    /**
     * Récupère la preuve de travail persistée, s'il y en a une.
     */
    suspend fun getToken(): Result<HashcashToken>

    /**
     * Supprime la preuve de travail (par exemple lors d'une révocation forcée d'identité).
     */
    suspend fun clearToken(): Result<Unit>
}
