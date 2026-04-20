package com.mobicloud.core.erasure

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.Random

/**
 * Instrumented roundtrip test for the native C++ erasure coding kernel.
 *
 * Runs on-device (or on emulator) so that the `mobimath_lib.so` matching the target ABI is
 * actually loaded. Cross-checks the native output against [PureKotlinErasureCodec] (same
 * GF(256) algorithm in pure Kotlin) to prove bit-exact parity between the two implementations.
 */
@RunWith(AndroidJUnit4::class)
class ErasureCodingJniTest {

    private val kotlinRef = PureKotlinErasureCodec()

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    @Test
    fun native_roundtrip_recovers_original_bytes_from_K_data_blocks() {
        val k = 4
        val n = 2
        val blockSize = 64 * 1024
        val rng = Random(0xC0FFEEL)
        val data = List(k) { ByteArray(blockSize).also(rng::nextBytes) }

        val parity = ErasureCodingJni.encode(data, k, n)
        assertEquals(n, parity.size)

        val recovered = ErasureCodingJni.decode(data, intArrayOf(0, 1, 2, 3), k, n)
        for (i in 0 until k) assertArrayEquals(data[i], recovered[i])
    }

    @Test
    fun native_roundtrip_recovers_from_mixed_data_and_parity_survivors() {
        val k = 4
        val n = 2
        val blockSize = 32 * 1024
        val rng = Random(0xFEEDL)
        val data = List(k) { ByteArray(blockSize).also(rng::nextBytes) }

        val parity = ErasureCodingJni.encode(data, k, n)
        // Drop data[2] and data[3] — survive with data[0], data[1], parity[0], parity[1].
        val survivors = listOf(data[0], data[1], parity[0], parity[1])
        val indices = intArrayOf(0, 1, 4, 5)

        val recovered = ErasureCodingJni.decode(survivors, indices, k, n)

        for (i in 0 until k) assertArrayEquals(data[i], recovered[i])
    }

    @Test
    fun native_parity_matches_pure_kotlin_reference_byte_for_byte() {
        val k = 4
        val n = 2
        val blockSize = 16 * 1024
        val rng = Random(0xBEEFL)
        val data = List(k) { ByteArray(blockSize).also(rng::nextBytes) }

        val nativeParity = ErasureCodingJni.encode(data, k, n)
        val kotlinParity = kotlinRef.encode(data, k, n)

        for (i in 0 until n) {
            assertArrayEquals(
                "Parity block $i diverges between native and Kotlin reference",
                kotlinParity[i],
                nativeParity[i],
            )
        }
        assertArrayEquals(
            sha256(kotlinParity.flatMap { it.toList() }.toByteArray()),
            sha256(nativeParity.flatMap { it.toList() }.toByteArray()),
        )
    }
}
