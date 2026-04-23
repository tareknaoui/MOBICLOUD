package com.mobicloud.domain.models

/**
 * Story 6.3 — exceptions scellées levées par le pipeline d'assemblage
 * (déchiffrement, décodage Erasure, vérification d'intégrité, écriture finale).
 *
 * Toutes ces exceptions provoquent la suppression du fichier temporaire en cours
 * d'écriture (cf. AssembleDownloadedFileUseCase) — aucun fichier partiel ne reste
 * jamais visible sur disque après un échec.
 */
sealed class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Aucune `wrappedMasterKey` n'est disponible dans le `CatalogEntry` — clé maîtresse impossible à dériver. */
    class MissingMasterKey(fileHash: String) :
        DownloadException("CatalogEntry $fileHash sans wrappedMasterKey")

    /** L'unwrap ECIES de la `wrappedMasterKey` a échoué (clé incorrecte, payload corrompu). */
    class MasterKeyUnwrap(cause: Throwable) :
        DownloadException("Unwrap master key échoué: ${cause.message}", cause)

    /** Un bloc individuel est corrompu (AES-GCM tag mismatch, taille invalide, fragmentIndex hors borne). */
    class CorruptBlock(detail: String) :
        DownloadException("Bloc corrompu: $detail")

    /** Le SHA-256 du fichier reconstitué ne correspond pas à `fileHash` annoncé dans le catalogue. */
    class CorruptFile(expected: String, actual: String) :
        DownloadException("Hash fichier invalide attendu=$expected actual=$actual")

    /**
     * IV ou `originalFileSize` absents (sentinelle "legacy" 12 × 0x00, ou 0L) — symptôme
     * d'un bloc/d'un catalogue généré avant Story 6.3 qui ne porte pas l'IV ou la taille.
     * Aucun déchiffrement tenté avec un IV non fourni.
     */
    class MasterKeyTransportGap(fileHash: String) :
        DownloadException("IV ou originalFileSize absent côté transport pour $fileHash")
}
