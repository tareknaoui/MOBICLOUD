package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import android.content.Context
import com.mobicloud.core.erasure.PureKotlinErasureCodec
import com.mobicloud.core.security.FileEnvelopeCipher
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.CipherScheme
import com.mobicloud.domain.models.DownloadException
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptionIdentity
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import kotlin.random.Random

/**
 * Chiffrer-puis-encoder : chiffrement du fichier entier puis Reed-Solomon sur le chiffré.
 * Vérifie l'aller-retour complet, la détection d'un fragment altéré, et la régénération
 * d'un fragment perdu sans la clé (propriété qui motive le changement d'ordre).
 */
class EncryptThenEncodeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val codec = PureKotlinErasureCodec()
    private val encoder = EncodeErasureFragmentsUseCase(codec)
    private val decoder = DecodeErasureFragmentsUseCase(codec)
    private val sealer = EncryptThenEncodeUseCase(FileEnvelopeCipher(), encoder)
    private val params = ErasureParameters(k = 4, n = 2)

    private lateinit var owner: KeyPair
    private lateinit var context: Context
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var securityRepository: SecurityRepository

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun sha256Hex(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        owner = ecKeyPair()
        context = mockk()
        every { context.cacheDir } returns tmp.newFolder("cache")
        catalogRepository = mockk()
        securityRepository = mockk()
        coEvery { securityRepository.getEncryptionIdentity() } returns
            Result.success(EncryptionIdentity(owner.public.encoded, owner.private))
    }

    private fun blocksOf(bundle: EncryptedBundle, indices: List<Int>): Map<Int, DownloadedBlock> =
        bundle.encryptedFragments.filter { it.index in indices }.associate { f ->
            f.index to DownloadedBlock(
                blockId = sha256Hex(f.ciphertext),
                fragmentIndex = f.index,
                isParity = f.isParity,
                ciphertext = f.ciphertext,
                iv = f.iv
            )
        }

    private fun catalogFor(plaintext: ByteArray, bundle: EncryptedBundle) = CatalogEntry(
        fileHash = sha256Hex(plaintext),
        ownerPubKeyHash = "owner",
        versionClock = 1L,
        fragmentLocations = emptyList(),
        wrappedMasterKey = bundle.wrappedFileMasterKey,
        originalFileSize = plaintext.size.toLong(),
        originalFileName = "doc.bin",
        k = params.k,
        n = params.n,
        cipherScheme = bundle.cipherScheme
    )

    private suspend fun assemble(plaintext: ByteArray, bundle: EncryptedBundle, blocks: Map<Int, DownloadedBlock>) =
        run {
            val entry = catalogFor(plaintext, bundle)
            coEvery { catalogRepository.getEntry(entry.fileHash) } returns Result.success(entry)
            AssembleDownloadedFileUseCase(context, catalogRepository, securityRepository, decoder, FileEnvelopeCipher())
                .invoke(entry.fileHash, blocks, isPreview = true).toList().last()
                as AssembleDownloadedFileUseCase.AssembleProgress.Finalized
        }

    @Test
    fun `upload produces ciphertext fragments sharing the file nonce and no plaintext`() = runTest {
        val plaintext = "MobiCloud ".repeat(500).toByteArray()
        val bundle = sealer(plaintext, owner.public.encoded, params).getOrThrow()

        assertEquals(CipherScheme.ENCRYPT_THEN_ENCODE, bundle.cipherScheme)
        assertEquals(plaintext.size.toLong(), bundle.plaintextSize)
        assertEquals(6, bundle.encryptedFragments.size)
        val nonce = bundle.encryptedFragments.first().iv
        assertTrue(bundle.encryptedFragments.all { it.iv.contentEquals(nonce) })
        assertTrue(bundle.encryptedFragments.all {
            it.originalFileSize == FileEnvelopeCipher.ciphertextSize(plaintext.size.toLong())
        })
        // RS systématique : le fragment de données 0 est un morceau du chiffré, pas du clair.
        val firstData = bundle.encryptedFragments.first { it.index == 0 }.ciphertext
        assertFalse(firstData.contentEquals(plaintext.copyOf(firstData.size)))
    }

    @Test
    fun `owner reassembles the file from any k fragments including parity`() = runTest {
        val plaintext = Random(7).nextBytes(10_003)
        val bundle = sealer(plaintext, owner.public.encoded, params).getOrThrow()

        for (subset in listOf(listOf(0, 1, 2, 3), listOf(1, 3, 4, 5), listOf(0, 2, 4, 5))) {
            val result = assemble(plaintext, bundle, blocksOf(bundle, subset)).result
            assertTrue("sous-ensemble $subset : $result", result is AssembleDownloadedFileUseCase.AssembleResult.Success)
            val path = (result as AssembleDownloadedFileUseCase.AssembleResult.Success).filePath
            assertArrayEquals(plaintext, File(path).readBytes())
        }
    }

    @Test
    fun `an altered fragment is detected before decoding`() = runTest {
        val plaintext = Random(8).nextBytes(4_096)
        val bundle = sealer(plaintext, owner.public.encoded, params).getOrThrow()
        val blocks = blocksOf(bundle, listOf(0, 1, 2, 4)).toMutableMap()
        val victim = blocks.getValue(2)
        blocks[2] = victim.copy(ciphertext = victim.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() })

        val result = assemble(plaintext, bundle, blocks).result
        assertTrue(result is AssembleDownloadedFileUseCase.AssembleResult.Failure)
        assertTrue((result as AssembleDownloadedFileUseCase.AssembleResult.Failure).exception is DownloadException.CorruptBlock)
    }

    @Test
    fun `another device's key cannot open the file`() = runTest {
        val plaintext = Random(9).nextBytes(2_048)
        val bundle = sealer(plaintext, owner.public.encoded, params).getOrThrow()
        val stranger = ecKeyPair()
        coEvery { securityRepository.getEncryptionIdentity() } returns
            Result.success(EncryptionIdentity(stranger.public.encoded, stranger.private))

        val result = assemble(plaintext, bundle, blocksOf(bundle, listOf(0, 1, 2, 3))).result
        assertTrue(result is AssembleDownloadedFileUseCase.AssembleResult.Failure)
        assertTrue((result as AssembleDownloadedFileUseCase.AssembleResult.Failure).exception is DownloadException.MasterKeyUnwrap)
    }

    @Test
    fun `lost fragments are regenerated without the key, byte for byte`() = runTest {
        val plaintext = Random(10).nextBytes(7_777)
        val bundle = sealer(plaintext, owner.public.encoded, params).getOrThrow()
        // Le réparateur ne reçoit que des fragments de chiffré (aucune clé, aucun SecurityRepository).
        val survivors = bundle.encryptedFragments.filter { it.index in setOf(1, 2, 4, 5) }.map {
            ErasureFragment(index = it.index, isParity = it.isParity, data = it.ciphertext, originalFileSize = it.originalFileSize)
        }

        val regenerated = RegenerateFragmentsUseCase(decoder, encoder)(survivors, params, setOf(0, 3)).getOrThrow()

        assertEquals(listOf(0, 3), regenerated.map { it.index })
        for (frag in regenerated) {
            val original = bundle.encryptedFragments.first { it.index == frag.index }.ciphertext
            assertArrayEquals("fragment ${frag.index}", original, frag.data)
            assertEquals("même blockId", sha256Hex(original), sha256Hex(frag.data))
        }
    }
}
