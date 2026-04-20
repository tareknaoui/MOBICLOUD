package com.mobicloud.core.erasure

import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object ErasureCodingJni : ErasureCodec {

    private val nativeLibraryAvailable: Boolean = runCatching {
        System.loadLibrary("mobimath_lib")
    }.onFailure { e ->
        Timber.e(e, "Failed to load native library 'mobimath_lib'. JNI calls will fail.")
    }.isSuccess

    private fun ensureNativeAvailable() {
        check(nativeLibraryAvailable) {
            "Native erasure-coding library 'mobimath_lib' unavailable on this ABI"
        }
    }

    private fun requireBufferFits(k: Int, blockSize: Int) {
        require(k.toLong() * blockSize <= Int.MAX_VALUE) {
            "erasure buffer size exceeds 2 GiB (k=$k * blockSize=$blockSize)"
        }
    }

    external fun nativeEncode(
        dataBuffer: ByteBuffer,
        parityBuffer: ByteBuffer,
        k: Int,
        n: Int,
        blockSize: Int,
    )

    external fun nativeDecode(
        survivorsBuffer: ByteBuffer,
        survivorIndicesBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        k: Int,
        n: Int,
        blockSize: Int,
    )

    override fun encode(data: List<ByteArray>, k: Int, n: Int): List<ByteArray> {
        ensureNativeAvailable()
        require(k >= 1 && n >= 1 && k + n <= 255) {
            "GF(256) constraint: k >= 1, n >= 1, k + n <= 255 (got k=$k, n=$n)"
        }
        require(data.size == k) { "data must contain exactly k=$k blocks, got ${data.size}" }
        val blockSize = data.first().size
        require(data.all { it.size == blockSize }) { "all data blocks must have the same length" }
        require(blockSize > 0) { "blockSize must be > 0" }
        requireBufferFits(k, blockSize)
        requireBufferFits(n, blockSize)

        val dataBuffer = ByteBuffer.allocateDirect(k * blockSize).order(ByteOrder.LITTLE_ENDIAN)
        for (block in data) dataBuffer.put(block)
        dataBuffer.rewind()

        val parityBuffer = ByteBuffer.allocateDirect(n * blockSize).order(ByteOrder.LITTLE_ENDIAN)

        nativeEncode(dataBuffer, parityBuffer, k, n, blockSize)

        return List(n) { i ->
            val out = ByteArray(blockSize)
            parityBuffer.position(i * blockSize)
            parityBuffer.get(out)
            out
        }
    }

    override fun decode(
        survivors: List<ByteArray>,
        survivorIndices: IntArray,
        k: Int,
        n: Int,
    ): List<ByteArray> {
        ensureNativeAvailable()
        require(k >= 1 && n >= 1 && k + n <= 255) {
            "GF(256) constraint: k >= 1, n >= 1, k + n <= 255 (got k=$k, n=$n)"
        }
        require(survivors.size == k) { "need exactly k=$k survivors, got ${survivors.size}" }
        require(survivorIndices.size == k) { "need exactly k=$k survivor indices" }
        val blockSize = survivors.first().size
        require(survivors.all { it.size == blockSize }) { "all survivors must have the same length" }
        require(blockSize > 0) { "blockSize must be > 0" }
        requireBufferFits(k, blockSize)

        val survivorsBuffer = ByteBuffer.allocateDirect(k * blockSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in survivors) survivorsBuffer.put(s)
        survivorsBuffer.rewind()

        val indicesBuffer = ByteBuffer.allocateDirect(k * Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (idx in survivorIndices) indicesBuffer.putInt(idx)
        indicesBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(k * blockSize).order(ByteOrder.LITTLE_ENDIAN)

        nativeDecode(survivorsBuffer, indicesBuffer, outputBuffer, k, n, blockSize)

        return List(k) { i ->
            val out = ByteArray(blockSize)
            outputBuffer.position(i * blockSize)
            outputBuffer.get(out)
            out
        }
    }
}
