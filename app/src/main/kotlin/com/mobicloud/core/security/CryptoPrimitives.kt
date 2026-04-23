package com.mobicloud.core.security

import com.mobicloud.domain.models.WrappedFileMasterKey
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Story 6.3 — primitives cryptographiques partagées entre [FragmentCipherUseCase]
 * (encrypt/decrypt) et [com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase]
 * (déchiffrement par bloc).
 *
 * `hkdfSha256` reste dans [HkdfSha256.kt] (impl existante RFC 5869). Ce fichier
 * extrait `unwrapFileMasterKey` (auparavant private dans `FragmentCipherUseCase`)
 * pour permettre sa réutilisation dans le pipeline d'assemblage sans dupliquer
 * le wiring ECDH + AES-GCM.
 */

/**
 * Story 6.3 — déchiffre la `fileMasterKey` (32 octets) wrappée via ECIES.
 *
 * Pattern strictement identique à l'ancienne implémentation privée de
 * `FragmentCipherUseCase.unwrapFileMasterKey` (qui consomme désormais ce helper).
 *
 * @throws java.security.GeneralSecurityException si la clé privée est incorrecte ou
 * si le payload AES-GCM est corrompu (tag mismatch).
 */
internal fun unwrapFileMasterKey(
    wrapped: WrappedFileMasterKey,
    recipientPrivateKey: PrivateKey
): ByteArray {
    val ephemeralPublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(wrapped.ephemeralPublicKeyBytes))

    val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
        init(recipientPrivateKey)
        doPhase(ephemeralPublicKey, true)
    }.generateSecret()

    val wrappingKey = hkdfSha256(ikm = sharedSecret, info = "ecies_key".toByteArray())
    try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(wrappingKey, "AES"),
                GCMParameterSpec(128, wrapped.iv)
            )
        }
        return cipher.doFinal(wrapped.encryptedKey)
    } finally {
        sharedSecret.fill(0)
        wrappingKey.fill(0)
    }
}
