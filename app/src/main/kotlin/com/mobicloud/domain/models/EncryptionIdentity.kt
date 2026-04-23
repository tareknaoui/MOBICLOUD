package com.mobicloud.domain.models

import java.security.PrivateKey

/**
 * Story 6.3 — Identité de chiffrement EC P-256 dédiée à l'ECIES.
 *
 * Distincte de [NodeIdentity] (clé Keystore hardware-backed `PURPOSE_SIGN | PURPOSE_VERIFY`,
 * incompatible avec ECDH sur API < 31). Cette paire est software-managed
 * (JCE), stockée chiffrée via `EncryptedSharedPreferences`. Voir Story 6.3 Contrainte #1.
 *
 * @property publicKeyBytes clé publique sérialisée X509 (`PublicKey.getEncoded()`).
 * @property privateKey clé privée résidente en mémoire (récupérée depuis le storage chiffré).
 */
data class EncryptionIdentity(
    val publicKeyBytes: ByteArray,
    val privateKey: PrivateKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptionIdentity) return false
        return publicKeyBytes.contentEquals(other.publicKeyBytes) &&
                privateKey == other.privateKey
    }

    override fun hashCode(): Int {
        var result = publicKeyBytes.contentHashCode()
        result = 31 * result + privateKey.hashCode()
        return result
    }
}
