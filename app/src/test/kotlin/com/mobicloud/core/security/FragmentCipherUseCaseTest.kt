package com.mobicloud.core.security

import com.mobicloud.domain.models.ErasureFragment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

class FragmentCipherUseCaseTest {

    private lateinit var useCase: FragmentCipherUseCase

    @Before
    fun setUp() {
        useCase = FragmentCipherUseCase()
    }

    private fun generateKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
    }.generateKeyPair()

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun makeFragments(count: Int = 3, sizeBytes: Int = 1024 * 1024): List<ErasureFragment> =
        (0 until count).map { i ->
            val data = ByteArray(sizeBytes).also { SecureRandom().nextBytes(it) }
            ErasureFragment(index = i, isParity = i >= 2, data = data, originalFileSize = sizeBytes.toLong())
        }

    @Test
    fun `encrypt then decrypt with correct key reproduces original fragments SHA-256`() = runTest {
        val keyPair = generateKeyPair()
        val fragments = makeFragments()
        val originalHashes = fragments.map { sha256(it.data) }

        val bundle = useCase.encrypt(fragments, keyPair.public.encoded).getOrThrow()
        val recovered = useCase.decrypt(bundle, keyPair.private).getOrThrow()

        for (i in fragments.indices) {
            assertArrayEquals(
                "Fragment $i SHA-256 mismatch",
                originalHashes[i],
                sha256(recovered[i].data)
            )
        }
    }

    @Test
    fun `decrypt with wrong private key returns Result failure`() = runTest {
        val keyPair = generateKeyPair()
        val wrongKeyPair = generateKeyPair()
        val fragments = makeFragments()

        val bundle = useCase.encrypt(fragments, keyPair.public.encoded).getOrThrow()
        val result = useCase.decrypt(bundle, wrongKeyPair.private)

        assertTrue("Expected failure with wrong key", result.isFailure)
    }

    @Test
    fun `decrypt with tampered fragment ciphertext returns Result failure`() = runTest {
        val keyPair = generateKeyPair()
        val fragments = makeFragments()

        val bundle = useCase.encrypt(fragments, keyPair.public.encoded).getOrThrow()

        val tamperedFragment = bundle.encryptedFragments[0].let { frag ->
            val tampered = frag.ciphertext.copyOf()
            tampered[0] = (tampered[0].toInt() xor 0xFF).toByte()
            frag.copy(ciphertext = tampered)
        }
        val tamperedBundle = bundle.copy(
            encryptedFragments = listOf(tamperedFragment) + bundle.encryptedFragments.drop(1)
        )

        val result = useCase.decrypt(tamperedBundle, keyPair.private)

        assertTrue("Expected failure with tampered ciphertext", result.isFailure)
    }

    @Test
    fun `decrypt with tampered WrappedFileMasterKey returns Result failure`() = runTest {
        val keyPair = generateKeyPair()
        val fragments = makeFragments()

        val bundle = useCase.encrypt(fragments, keyPair.public.encoded).getOrThrow()

        val tamperedEncryptedKey = bundle.wrappedFileMasterKey.encryptedKey.copyOf()
        tamperedEncryptedKey[0] = (tamperedEncryptedKey[0].toInt() xor 0xFF).toByte()
        val tamperedWrapped = bundle.wrappedFileMasterKey.copy(encryptedKey = tamperedEncryptedKey)
        val tamperedBundle = bundle.copy(wrappedFileMasterKey = tamperedWrapped)

        val result = useCase.decrypt(tamperedBundle, keyPair.private)

        assertTrue("Expected failure with tampered WrappedFileMasterKey", result.isFailure)
    }

    @Test
    fun `encrypt with empty fragments list returns Result failure with IllegalArgumentException`() = runTest {
        val keyPair = generateKeyPair()

        val result = useCase.encrypt(emptyList(), keyPair.public.encoded)

        assertTrue("Expected failure for empty fragments", result.isFailure)
        assertTrue(
            "Expected IllegalArgumentException",
            result.exceptionOrNull() is IllegalArgumentException
        )
    }
}
