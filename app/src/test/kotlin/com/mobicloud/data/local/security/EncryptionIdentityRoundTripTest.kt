package com.mobicloud.data.local.security

import com.mobicloud.core.security.hkdfSha256
import com.mobicloud.core.security.unwrapFileMasterKey
import com.mobicloud.domain.models.WrappedFileMasterKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Story 6.3 — tests JVM purs sur la round-trip d'`EncryptionIdentity`. Pas d'`EncryptedSharedPreferences`
 * (nécessite Robolectric) — on se concentre sur les deux briques cryptographiques :
 *  1. Sérialisation PKCS#8 → re-désérialisation == clé fonctionnelle.
 *  2. ECDH round-trip : encrypter avec `publicKeyBytes`, déchiffrer avec la `privateKey` — chemin
 *     que Story 6.3 emprunte côté `AssembleDownloadedFileUseCase`.
 */
class EncryptionIdentityRoundTripTest {

    private val secureRandom = SecureRandom()

    @Test
    fun `Story 6_3 — PKCS8 encode-decode renvoie une clé fonctionnellement equivalente`() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val pkcs8Bytes = kp.private.encoded
        assertNotNull(pkcs8Bytes)

        val decoded = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))

        // La clé est fonctionnellement équivalente : `encoded` est stable pour le même format.
        assertArrayEquals(pkcs8Bytes, decoded.encoded)
    }

    @Test
    fun `Story 6_3 — ECIES round-trip via unwrapFileMasterKey decoie une clé wrappée`() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }

        // Wrap (mêmes primitives que FragmentCipherUseCase.wrapFileMasterKey)
        val ephemeralKp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(ephemeralKp.private)
            doPhase(kp.public, true)
        }.generateSecret()
        val wrappingKey = hkdfSha256(ikm = sharedSecret, info = "ecies_key".toByteArray())
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, iv))
        }
        val encryptedKey = cipher.doFinal(fileMasterKey)
        val wrapped = WrappedFileMasterKey(
            ephemeralPublicKeyBytes = ephemeralKp.public.encoded,
            iv = iv,
            encryptedKey = encryptedKey
        )

        // Story 6.3 path : décode via la même clé privée (issue d'EncryptionIdentity)
        val unwrapped = unwrapFileMasterKey(wrapped, kp.private)

        assertArrayEquals(
            "le déchiffrement ECIES doit reproduire la fileMasterKey originale",
            fileMasterKey,
            unwrapped
        )
    }
}
