package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.core.erasure.PureKotlinErasureCodec
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.ErasureParameters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.Random

/**
 * Host-JVM roundtrip tests for the Erasure Coding UseCases.
 *
 * The native `.so` cannot be loaded on a Windows/x86_64 host, so the UseCases are exercised with
 * a pure-Kotlin reference codec ([PureKotlinErasureCodec]) that implements the same GF(256)
 * algebra. This validates the UseCase-level contract (padding, ordering, Result handling,
 * K-1 fragment failure). Bit-exact parity with the native kernel is further validated by the
 * androidTest suite on real devices.
 */
class ErasureRoundtripTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val codec = PureKotlinErasureCodec()
    private val encode = EncodeErasureFragmentsUseCase(codec)
    private val decode = DecodeErasureFragmentsUseCase(codec)

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun randomFile(sizeBytes: Int, seed: Long = 0xC0FFEEL): File {
        val f = temporaryFolder.newFile()
        val payload = ByteArray(sizeBytes)
        Random(seed).nextBytes(payload)
        f.writeBytes(payload)
        return f
    }

    @Test
    fun `encode then decode first K blocks reproduces the original file bit-for-bit`() = runTest {
        val file = randomFile(2_621_440)  // 2.5 MiB
        val original = file.readBytes()

        val fragments = encode(file).getOrThrow()
        assertEquals(6, fragments.size)
        assertEquals(4, fragments.count { !it.isParity })
        assertEquals(2, fragments.count { it.isParity })

        val recovered = decode(fragments.take(4)).getOrThrow()

        assertArrayEquals(sha256(original), sha256(recovered))
        assertArrayEquals(original, recovered)
    }

    @Test
    fun `decode survives loss of the 2 first data blocks by using parity`() = runTest {
        val file = randomFile(2_621_440)
        val original = file.readBytes()

        val fragments = encode(file).getOrThrow()
        // Keep blocks [2, 3, 4, 5] — i.e. last 2 data + both parity.
        val survivors = fragments.subList(2, 6)

        val recovered = decode(survivors).getOrThrow()

        assertArrayEquals(sha256(original), sha256(recovered))
    }

    @Test
    fun `decode survives a mixed selection of data and parity fragments`() = runTest {
        val file = randomFile(2_621_440)
        val original = file.readBytes()

        val fragments = encode(file).getOrThrow()
        // Keep data[0], data[1], parity[0], parity[1] — drops data[2] and data[3].
        val survivors = listOf(fragments[0], fragments[1], fragments[4], fragments[5])

        val recovered = decode(survivors).getOrThrow()

        assertArrayEquals(sha256(original), sha256(recovered))
    }

    @Test
    fun `decode with only K-1 fragments returns a Failure Result`() = runTest {
        val file = randomFile(2_621_440)

        val fragments = encode(file).getOrThrow()
        val tooFew = fragments.take(3)  // K - 1 = 3

        val result = decode(tooFew)

        assertTrue("Expected failure when given K-1 fragments", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `default parameters K equals 4 and N equals 2 are applied when none are provided`() = runTest {
        val file = randomFile(2_621_440)

        val fragments = encode(file).getOrThrow()

        assertEquals(4, fragments.count { !it.isParity })
        assertEquals(2, fragments.count { it.isParity })
        assertEquals(0, fragments.first().index)
        assertEquals(5, fragments.last().index)
        // Defaults are the ones documented in ErasureParameters.
        assertEquals(4, ErasureParameters().k)
        assertEquals(2, ErasureParameters().n)
    }

    @Test
    fun `fragments are emitted in index order with a stable data then parity layout`() = runTest {
        val file = randomFile(2_621_440)

        val fragments: List<ErasureFragment> = encode(file).getOrThrow()

        for ((i, fragment) in fragments.withIndex()) {
            assertEquals(i, fragment.index)
            assertEquals(i >= 4, fragment.isParity)
        }
    }
}
