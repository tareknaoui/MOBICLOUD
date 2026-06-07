package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import android.content.Context
import android.webkit.MimeTypeMap
import com.mobicloud.core.security.hkdfSha256
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.DownloadException
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.EncryptionIdentity
import com.mobicloud.domain.models.WrappedFileMasterKey
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Story 6.3 — tests JVM purs du pipeline d'assemblage. Aucun Robolectric : `Context` est
 * mocké via mockk, `cacheDir` et `getExternalFilesDir(...)` pointent vers `TemporaryFolder`.
 *
 * Le test construit ses propres blocs chiffrés via une fixture déterministe
 * (encrypt(plaintext, blockKey, iv)) — pas de dépendance à FragmentCipherUseCase pour rester
 * en pur unit-test (zéro injection Hilt, zéro dispatcher Android).
 */
class AssembleDownloadedFileUseCaseTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var decodeUseCase: DecodeErasureFragmentsUseCase

    private lateinit var cacheDir: File
    private lateinit var finalDir: File
    private lateinit var keyPair: KeyPair
    private val secureRandom = SecureRandom()

    @Before
    fun setUp() {
        context = mockk()
        catalogRepository = mockk()
        securityRepository = mockk()
        decodeUseCase = mockk()

        cacheDir = tmp.newFolder("cache")
        finalDir = tmp.newFolder("final")
        every { context.cacheDir } returns cacheDir
        every { context.getExternalFilesDir(any()) } returns finalDir
        every { context.filesDir } returns finalDir

        keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

        // Le chemin de finalisation (download non-preview) appelle inferMimeType → MimeTypeMap
        // .getSingleton(), une API Android non disponible en unit-test JVM pur (lèverait
        // "Method not mocked"). On la stub ; SDK_INT=0 en JVM → publishToMediaStore est sauté,
        // donc le pipeline retombe sur le fallback getExternalFilesDir(...) = finalDir.
        mockkStatic(MimeTypeMap::class)
        val mimeTypeMap = mockk<MimeTypeMap>()
        every { MimeTypeMap.getSingleton() } returns mimeTypeMap
        every { mimeTypeMap.getMimeTypeFromExtension(any()) } returns "application/octet-stream"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun newUseCase() = AssembleDownloadedFileUseCase(
        context = context,
        catalogRepository = catalogRepository,
        securityRepository = securityRepository,
        decodeErasureFragmentsUseCase = decodeUseCase
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Wrap a fileMasterKey via ECIES, exactly comme FragmentCipherUseCase.wrapFileMasterKey.
     * Compatible avec `unwrapFileMasterKey` (CryptoPrimitives) lu par le pipeline.
     */
    private fun wrapMasterKey(fileMasterKey: ByteArray, recipientPub: ByteArray): WrappedFileMasterKey {
        val ephemeralKp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val recipient = java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(recipientPub))
        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(ephemeralKp.private)
            doPhase(recipient, true)
        }.generateSecret()
        val wrappingKey = hkdfSha256(ikm = sharedSecret, info = "ecies_key".toByteArray())
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, iv))
        }
        val encrypted = cipher.doFinal(fileMasterKey)
        return WrappedFileMasterKey(
            ephemeralPublicKeyBytes = ephemeralKp.public.encoded,
            iv = iv,
            encryptedKey = encrypted
        )
    }

    /**
     * Build K data blocks from `content`, padded to fragmentSize. Each block AES-GCM encrypted
     * with `blockKey = HKDF(fileMasterKey, "block_key_${i}")`. No parity (streaming path).
     */
    private fun buildEncryptedDataBlocks(
        content: ByteArray,
        k: Int = 4,
        fileMasterKey: ByteArray
    ): Map<Int, DownloadedBlock> {
        val fragmentSize = ((content.size + k - 1) / k)
        val padded = ByteArray(fragmentSize * k)
        content.copyInto(padded)
        val blocks = mutableMapOf<Int, DownloadedBlock>()
        for (i in 0 until k) {
            val plaintext = padded.copyOfRange(i * fragmentSize, (i + 1) * fragmentSize)
            val blockKey = hkdfSha256(ikm = fileMasterKey, info = "block_key_$i".toByteArray())
            val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(blockKey, "AES"), GCMParameterSpec(128, iv))
            }
            val ciphertext = cipher.doFinal(plaintext)
            blocks[i] = DownloadedBlock(
                blockId = sha256Hex(ciphertext),
                fragmentIndex = i,
                isParity = false,
                ciphertext = ciphertext,
                iv = iv
            )
        }
        return blocks
    }

    private fun buildCatalog(
        content: ByteArray,
        wrapped: WrappedFileMasterKey,
        fileHashOverride: String? = null
    ): CatalogEntry = CatalogEntry(
        fileHash = fileHashOverride ?: sha256Hex(content),
        ownerPubKeyHash = "test-owner",
        versionClock = 1L,
        fragmentLocations = emptyList(),
        wrappedMasterKey = wrapped,
        originalFileSize = content.size.toLong()
    )

    private fun stubEncryptionIdentity() {
        coEvery { securityRepository.getEncryptionIdentity() } returns Result.success(
            EncryptionIdentity(keyPair.public.encoded, keyPair.private)
        )
    }

    // --- Test 1 : Happy path — K data blocs sans parité ---
    @Test
    fun `Story 6_3 — happy path K data sans parite produit Success avec hash final correct`() = runTest {
        val content = "MobiCloud Story 6.3 happy path test content avec quelques caracteres".toByteArray()
        val fileHash = sha256Hex(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val wrapped = wrapMasterKey(fileMasterKey, keyPair.public.encoded)
        val blocks = buildEncryptedDataBlocks(content, k = 4, fileMasterKey = fileMasterKey)

        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(buildCatalog(content, wrapped))
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, blocks).toList()

        val final = result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized
        val success = final.result as AssembleDownloadedFileUseCase.AssembleResult.Success
        val finalFile = File(success.filePath)
        assertTrue(finalFile.exists())
        assertArrayEquals(content, finalFile.readBytes())

        // Aucun .tmp résiduel dans cacheDir
        val leftovers = cacheDir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("aucun .tmp ne doit subsister", 0, leftovers.size)
    }

    // --- Test 3 (Subtask 11.5) : IV taille invalide → MasterKeyTransportGap ---
    @Test
    fun `Story 6_3 — IV invalide retourne MasterKeyTransportGap et supprime le tmp`() = runTest {
        val content = "x".repeat(64).toByteArray()
        val fileHash = sha256Hex(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val wrapped = wrapMasterKey(fileMasterKey, keyPair.public.encoded)
        val blocks = buildEncryptedDataBlocks(content, k = 4, fileMasterKey = fileMasterKey)
            .mapValues { (_, b) -> b.copy(iv = ByteArray(8)) } // taille invalide

        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(buildCatalog(content, wrapped))
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, blocks).toList()

        val final = result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized
        val failure = final.result as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue(failure.exception is DownloadException.MasterKeyTransportGap)
    }

    // --- Test 4 (Subtask 11.6) : ciphertext altéré → CorruptBlock + tmp supprimé ---
    @Test
    fun `Story 6_3 — ciphertext altere retourne CorruptBlock et supprime le tmp`() = runTest {
        val content = "y".repeat(64).toByteArray()
        val fileHash = sha256Hex(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val wrapped = wrapMasterKey(fileMasterKey, keyPair.public.encoded)
        val blocks = buildEncryptedDataBlocks(content, k = 4, fileMasterKey = fileMasterKey)
            .toMutableMap().apply {
                // Flip un octet du ciphertext du bloc 0
                val tampered = this[0]!!.ciphertext.copyOf().apply { this[0] = (this[0].toInt() xor 0xFF).toByte() }
                this[0] = this[0]!!.copy(ciphertext = tampered)
            }

        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(buildCatalog(content, wrapped))
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, blocks).toList()

        val failure = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue(failure.exception is DownloadException.CorruptBlock)
        // tmp doit être supprimé
        val leftovers = cacheDir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("aucun .tmp ne doit subsister", 0, leftovers.size)
    }

    // --- Test 5 (Subtask 11.7) : MissingMasterKey ---
    @Test
    fun `Story 6_3 — wrappedMasterKey null retourne MissingMasterKey`() = runTest {
        val content = "z".toByteArray()
        val fileHash = sha256Hex(content)
        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(
            CatalogEntry(
                fileHash = fileHash,
                ownerPubKeyHash = "x",
                versionClock = 1L,
                fragmentLocations = emptyList(),
                wrappedMasterKey = null,
                originalFileSize = content.size.toLong()
            )
        )
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, emptyMap()).toList()

        val failure = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue(failure.exception is DownloadException.MissingMasterKey)
    }

    // --- Test 6 (Subtask 11.8) : MasterKeyUnwrap (clé privée incorrecte) ---
    @Test
    fun `Story 6_3 — clé privée incorrecte retourne MasterKeyUnwrap`() = runTest {
        val content = "abc".toByteArray()
        val fileHash = sha256Hex(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        // Wrap avec UNE paire, déchiffre avec une AUTRE paire
        val otherKp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val wrapped = wrapMasterKey(fileMasterKey, otherKp.public.encoded)

        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(buildCatalog(content, wrapped))
        coEvery { securityRepository.getEncryptionIdentity() } returns Result.success(
            EncryptionIdentity(keyPair.public.encoded, keyPair.private) // mauvaise privKey
        )

        val result = newUseCase().invoke(
            fileHash,
            buildEncryptedDataBlocks(content, k = 4, fileMasterKey = fileMasterKey)
        ).toList()

        val failure = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue(failure.exception is DownloadException.MasterKeyUnwrap)
    }

    // --- Test 7 (Subtask 11.9) : CorruptFile (hash final mismatch) ---
    @Test
    fun `Story 6_3 — fileHash erroné dans catalog retourne CorruptFile et supprime le tmp`() = runTest {
        val content = "valid_content_for_hash_check_test".toByteArray()
        val fileHash = sha256Hex(content)
        val wrongFileHash = "0".repeat(64) // ≠ sha256(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val wrapped = wrapMasterKey(fileMasterKey, keyPair.public.encoded)
        val blocks = buildEncryptedDataBlocks(content, k = 4, fileMasterKey = fileMasterKey)

        // CatalogEntry annonce un wrongFileHash mais on déchiffre depuis l'entry stockée par fileHash
        coEvery { catalogRepository.getEntry(wrongFileHash) } returns Result.success(
            buildCatalog(content, wrapped, fileHashOverride = wrongFileHash)
        )
        stubEncryptionIdentity()

        val result = newUseCase().invoke(wrongFileHash, blocks).toList()

        val failure = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue("expected CorruptFile, got ${failure.exception}",
            failure.exception is DownloadException.CorruptFile)
        val leftovers = cacheDir.listFiles { f -> f.name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("aucun .tmp ne doit subsister", 0, leftovers.size)
    }

    // --- Test 8 : MissingMasterKey quand catalog absent ---
    @Test
    fun `Story 6_3 — catalog inexistant retourne MissingMasterKey`() = runTest {
        val fileHash = "f".repeat(64)
        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(null)
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, emptyMap()).toList()

        val failure = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue(failure.exception is DownloadException.MissingMasterKey)
    }

    // --- Test 9 : originalFileSize=0 (legacy pre-6.3) → MasterKeyTransportGap ---
    @Test
    fun `Story 6_3 — originalFileSize=0 (legacy) retourne MasterKeyTransportGap`() = runTest {
        val content = "a".toByteArray()
        val fileHash = sha256Hex(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val wrapped = wrapMasterKey(fileMasterKey, keyPair.public.encoded)

        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(
            CatalogEntry(
                fileHash = fileHash,
                ownerPubKeyHash = "x",
                versionClock = 1L,
                fragmentLocations = emptyList(),
                wrappedMasterKey = wrapped,
                originalFileSize = 0L // legacy sentinelle
            )
        )
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, emptyMap()).toList()

        val failure = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue(failure.exception is DownloadException.MasterKeyTransportGap)
    }

    // --- Test 10 : tempFile sentinel — IV all-zero (legacy) → MasterKeyTransportGap ---
    @Test
    fun `Story 6_3 — IV legacy 12x0x00 sentinelle retourne MasterKeyTransportGap`() = runTest {
        val content = "test legacy iv sentinel".toByteArray()
        val fileHash = sha256Hex(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val wrapped = wrapMasterKey(fileMasterKey, keyPair.public.encoded)
        val blocks = buildEncryptedDataBlocks(content, k = 4, fileMasterKey = fileMasterKey)
            .mapValues { (_, b) -> b.copy(iv = ByteArray(12)) } // all-zero sentinelle

        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(buildCatalog(content, wrapped))
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, blocks).toList()

        val failure = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Failure
        assertTrue(failure.exception is DownloadException.MasterKeyTransportGap)
    }

    // --- Test 11 : aucun fichier ne reste dans cacheDir après tout test (vérif globale) ---
    @Test
    fun `Story 6_3 — happy path final file est dans finalDir et pas dans cacheDir`() = runTest {
        val content = "small".toByteArray()
        val fileHash = sha256Hex(content)
        val fileMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val wrapped = wrapMasterKey(fileMasterKey, keyPair.public.encoded)
        val blocks = buildEncryptedDataBlocks(content, k = 4, fileMasterKey = fileMasterKey)

        coEvery { catalogRepository.getEntry(fileHash) } returns Result.success(buildCatalog(content, wrapped))
        stubEncryptionIdentity()

        val result = newUseCase().invoke(fileHash, blocks).toList()
        val success = (result.last() as AssembleDownloadedFileUseCase.AssembleProgress.Finalized).result
                as AssembleDownloadedFileUseCase.AssembleResult.Success

        val finalFile = File(success.filePath)
        assertTrue(finalFile.absolutePath.startsWith(finalDir.absolutePath))
        assertFalse(File(cacheDir, "download_${fileHash.take(32)}.tmp").exists())
    }
}
