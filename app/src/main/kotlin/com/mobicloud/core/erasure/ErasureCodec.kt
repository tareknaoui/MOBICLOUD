package com.mobicloud.core.erasure

/**
 * Reed-Solomon Erasure Coding contract over GF(256).
 *
 * The interface is kept free of Android / NIO types so that the domain layer can depend on it
 * without touching platform APIs. The production implementation is [ErasureCodingJni] which
 * bridges to a native C++ kernel via DirectByteBuffer (zero-copy). Unit tests inject a pure-Kotlin
 * fake that exercises the same GF(256) algebra on the host JVM.
 */
interface ErasureCodec {

    /**
     * Encodes [k] equal-length data blocks into [n] parity blocks.
     *
     * @param data exactly [k] blocks, all of the same length.
     * @param k number of data blocks (must match [data].size).
     * @param n number of parity blocks to produce.
     * @return [n] parity blocks, each the same length as the input blocks.
     */
    fun encode(data: List<ByteArray>, k: Int, n: Int): List<ByteArray>

    /**
     * Reconstructs the original [k] data blocks from any [k] survivors (data or parity mixed).
     *
     * @param survivors exactly [k] blocks of the same length.
     * @param survivorIndices indices in [0, k+n) identifying which rows of the systematic
     *                        (K+N)×K generator matrix the survivors correspond to.
     * @param k number of data blocks in the original encoding.
     * @param n number of parity blocks in the original encoding.
     * @return the [k] data blocks in their original order.
     */
    fun decode(
        survivors: List<ByteArray>,
        survivorIndices: IntArray,
        k: Int,
        n: Int,
    ): List<ByteArray>
}
