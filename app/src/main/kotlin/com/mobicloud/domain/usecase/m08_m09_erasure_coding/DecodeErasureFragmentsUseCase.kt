package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.core.erasure.ErasureCodec
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.ErasureParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reconstructs the original file bytes from any K fragments out of K+N.
 *
 * Picks the first [ErasureParameters.k] fragments from the input list (callers are free to
 * pre-filter which ones to pass), feeds them to the injected [ErasureCodec] along with their
 * indices, concatenates the recovered data blocks, then trims to the original file size.
 */
class DecodeErasureFragmentsUseCase @Inject constructor(
    private val codec: ErasureCodec,
) {

    suspend operator fun invoke(
        fragments: List<ErasureFragment>,
        params: ErasureParameters = ErasureParameters(),
    ): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            require(params.k >= 1 && params.n >= 1 && params.k + params.n <= 255) {
                "GF(256) constraint: k >= 1, n >= 1, k + n <= 255 (got k=${params.k}, n=${params.n})"
            }
            require(fragments.size >= params.k) {
                "Need at least k=${params.k} fragments to reconstruct, got ${fragments.size}"
            }

            val selected = fragments.take(params.k)

            val indexRange = 0 until (params.k + params.n)
            require(selected.all { it.index in indexRange }) {
                "Fragment indices must be in [0, ${params.k + params.n}); got ${selected.map { it.index }}"
            }
            require(selected.map { it.index }.distinct().size == params.k) {
                "Duplicate fragment indices: ${selected.map { it.index }}"
            }

            val originalSize = selected.first().originalFileSize
            require(selected.all { it.originalFileSize == originalSize }) {
                "Fragments disagree on originalFileSize: ${selected.map { it.originalFileSize }}"
            }

            val survivors = selected.map { it.data }
            val indices = selected.map { it.index }.toIntArray()

            val decoded = codec.decode(survivors, indices, params.k, params.n)

            val fragmentSize = decoded.first().size
            val assembledSize = fragmentSize.toLong() * params.k
            require(assembledSize <= Int.MAX_VALUE) {
                "Assembled buffer exceeds 2 GiB (fragmentSize=$fragmentSize, k=${params.k})"
            }
            require(originalSize in 0..assembledSize) {
                "originalFileSize=$originalSize out of bounds [0, $assembledSize]"
            }

            val assembled = ByteArray(assembledSize.toInt())
            for ((i, block) in decoded.withIndex()) {
                System.arraycopy(block, 0, assembled, i * fragmentSize, block.size)
            }
            assembled.copyOf(originalSize.toInt())
        }
    }
}
