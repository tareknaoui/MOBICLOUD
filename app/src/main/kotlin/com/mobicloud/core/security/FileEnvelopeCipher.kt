package com.mobicloud.core.security

import com.mobicloud.domain.models.WrappedFileMasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Fichier chiffré d'un seul tenant, avant encodage Reed-Solomon. */
class SealedFile(
    /** Chiffré AES-256-GCM, tag de 16 octets inclus en fin. */
    val ciphertext: ByteArray,
    /** Nonce GCM de 12 octets, unique par fichier. */
    val nonce: ByteArray,
    /** `fileMasterKey` chiffrée pour le propriétaire (ECIES). */
    val wrappedFileMasterKey: WrappedFileMasterKey
)

/**
 * Chiffrement du fichier entier pour le schéma **chiffrer-puis-encoder**.
 *
 * - `fileMasterKey` (K_F) : 32 octets aléatoires, chiffrée pour le propriétaire (ECIES).
 * - clé de fichier = HKDF-SHA256(K_F, "file_key_v1"), AES-256-GCM, nonce aléatoire de 12 octets,
 *   données associées [AAD] (lie le chiffré au schéma et empêche sa réinterprétation).
 *
 * Le chiffré est ensuite découpé par Reed-Solomon : les fragments ne contiennent que du chiffré,
 * et n'importe quel nœud détenant k fragments peut régénérer les autres sans connaître K_F.
 */
@Singleton
class FileEnvelopeCipher @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun seal(plaintext: ByteArray, recipientPublicKeyBytes: ByteArray): SealedFile {
        require(plaintext.isNotEmpty()) { "plaintext must not be empty" }
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        try {
            val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
            val ciphertext = withFileKey(fileMasterKey) { key ->
                Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                    updateAAD(AAD)
                    doFinal(plaintext)
                }
            }
            val wrapped = wrapFileMasterKey(fileMasterKey, recipientPublicKeyBytes, secureRandom)
            return SealedFile(ciphertext, nonce, wrapped)
        } finally {
            fileMasterKey.fill(0)
        }
    }

    /**
     * @throws java.security.GeneralSecurityException si le chiffré, le nonce ou la clé ne
     *   correspondent pas (tag GCM invalide).
     */
    fun open(ciphertext: ByteArray, nonce: ByteArray, fileMasterKey: ByteArray): ByteArray =
        withFileKey(fileMasterKey) { key ->
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(AAD)
                doFinal(ciphertext)
            }
        }

    private inline fun <T> withFileKey(fileMasterKey: ByteArray, block: (ByteArray) -> T): T {
        val key = hkdfSha256(ikm = fileMasterKey, info = FILE_KEY_INFO)
        try {
            return block(key)
        } finally {
            key.fill(0)
        }
    }

    companion object {
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        private const val TAG_BITS = TAG_BYTES * 8
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val FILE_KEY_INFO = "file_key_v1".toByteArray()
        private val AAD = "mobicloud|encrypt-then-encode|v1".toByteArray()

        /** Taille du chiffré pour un clair de [plaintextSize] octets. */
        fun ciphertextSize(plaintextSize: Long): Long = plaintextSize + TAG_BYTES
    }
}
