package com.mobicloud.domain.repository

import com.mobicloud.domain.models.HostedBlockPayload
import kotlinx.coroutines.flow.Flow

interface HostedBlockRepository {
    suspend fun saveBlock(
        blockId: String,
        ownerId: String,
        fragmentIndex: Int,
        isParity: Boolean,
        ciphertext: ByteArray,
        iv: ByteArray
    ): Result<String>

    suspend fun blockExists(blockId: String): Boolean

    suspend fun deleteBlock(blockId: String)

    /**
     * Story 6.2 — lecture d'un bloc hébergé localement pour le servir via TCP.
     *
     * @return `Success(payload)` si le bloc est trouvé et lisible,
     *         `Success(null)` si absent (DB ou disque) ou si [blockId] a un format invalide,
     *         `Failure(t)` en cas d'erreur I/O irrécupérable.
     */
    suspend fun getBlock(blockId: String): Result<HostedBlockPayload?>

    /** Story 7.1 — retourne tous les blockId actuellement hébergés localement. */
    suspend fun getAllBlockIds(): Result<List<String>>

    /** Story 1.6 — somme en temps réel des octets hébergés (pour affichage quota settings). */
    fun observeTotalHostedBytes(): Flow<Long>

    /**
     * Story 9.2 — lecture one-shot (suspend) de la somme totale des octets hébergés.
     * Complément de [observeTotalHostedBytes] : utilisée par `SignalingRepositoryImpl.registerAsSuperPeer`
     * pour calculer `freeBytes` au moment de l'envoi de REGISTER_PEER (snapshot best-effort, pas de Flow).
     */
    suspend fun getTotalHostedBytes(): Long
}
