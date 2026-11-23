package com.mobicloud.core.security

import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.WrappedFileMasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FragmentCipherUseCase @Inject constructor() {

    private val secureRandom = SecureRandom()

    suspend fun encrypt(
        fragments: List<ErasureFragment>,
        recipientPublicKeyBytes: ByteArray
    ): Result<EncryptedBundle> = withContext(Dispatchers.Default) {
        try {
            require(fragments.isNotEmpty()) { "fragments must not be empty" }

            val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
            try {
                val encryptedFragments = fragments.map { fragment ->
                    val blockKey = hkdfSha256(
                        ikm = fileMasterKey,
                        info = "block_key_${fragment.index}".toByteArray()
                    )
                    try {
                        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(
                                Cipher.ENCRYPT_MODE,
                                SecretKeySpec(blockKey, "AES"),
                                GCMParameterSpec(128, iv)
                            )
                        }
                        val ciphertext = cipher.doFinal(fragment.data)
                        EncryptedFragment(
                            index = fragment.index,
                            isParity = fragment.isParity,
                            ciphertext = ciphertext,
                            iv = iv,
                            originalFileSize = fragment.originalFileSize
                        )
                    } finally {
                        blockKey.fill(0)
                    }
                }

                val wrappedKey = wrapFileMasterKey(fileMasterKey, recipientPublicKeyBytes, secureRandom)
                Result.success(EncryptedBundle(encryptedFragments, wrappedKey))
            } finally {
                fileMasterKey.fill(0)
            }
        } catch (e: GeneralSecurityException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    suspend fun decrypt(
        bundle: EncryptedBundle,
        recipientPrivateKey: PrivateKey
    ): Result<List<ErasureFragment>> = withContext(Dispatchers.Default) {
        try {
            val fileMasterKey = unwrapFileMasterKey(bundle.wrappedFileMasterKey, recipientPrivateKey)
            try {
                val fragments = bundle.encryptedFragments.map { encFragment ->
                    val blockKey = hkdfSha256(
                        ikm = fileMasterKey,
                        info = "block_key_${encFragment.index}".toByteArray()
                    )
                    try {
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(
                                Cipher.DECRYPT_MODE,
                                SecretKeySpec(blockKey, "AES"),
                                GCMParameterSpec(128, encFragment.iv)
                            )
                        }
                        val plaintext = cipher.doFinal(encFragment.ciphertext)
                        ErasureFragment(
                            index = encFragment.index,
                            isParity = encFragment.isParity,
                            data = plaintext,
                            originalFileSize = encFragment.originalFileSize
                        )
                    } finally {
                        blockKey.fill(0)
                    }
                }
                Result.success(fragments)
            } finally {
                fileMasterKey.fill(0)
            }
        } catch (e: GeneralSecurityException) {
            Result.failure(e)
        }
    }

    // Story 6.3 — `unwrapFileMasterKey` extrait en `core/security/CryptoPrimitives.kt`
    // pour permettre la réutilisation par AssembleDownloadedFileUseCase sans dupliquer
    // le wiring ECDH + AES-GCM. Le helper top-level conserve un comportement strictement
    // identique à l'ancienne méthode privée (zéro régression sur Story 5.2).
}
