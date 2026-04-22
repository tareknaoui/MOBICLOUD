package com.mobicloud.domain.repository

import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.ResolvedBlockLocation

/**
 * Story 6.2 — abstraction du téléchargement TCP d'un bloc unique.
 *
 * L'implémentation par défaut est `BlockDownloadClient`. Cette interface est exposée pour
 * permettre l'injection d'un mock dans les tests JVM du `DownloadFileBlocksUseCase`.
 */
interface BlockDownloader {
    /**
     * Télécharge un seul bloc depuis [location].
     *
     * Vérifie systématiquement `sha256(ciphertext) == blockId` (défense en profondeur).
     *
     * @return `Success(DownloadedBlock)` si tout est OK, sinon `Failure` avec :
     *   - `IOException` (BLOCK_NOT_FOUND, I/O, socket fermée)
     *   - `SocketTimeoutException` (timeout serveur)
     *   - `SecurityException` (hash mismatch)
     *   - `IllegalStateException` (frame invalide / discriminateur inattendu)
     */
    suspend fun downloadBlock(
        location: ResolvedBlockLocation,
        timeoutMs: Long
    ): Result<DownloadedBlock>
}
