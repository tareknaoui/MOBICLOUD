package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.core.erasure.ErasureCodec
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.ErasureParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Encodes a file into K data + N parity fragments using Reed-Solomon over GF(256).
 *
 * The file is zero-padded to the next multiple of K bytes, split into K equal-length blocks,
 * then fed to the injected [ErasureCodec] in a single batched call. Produces a deterministic
 * ordering: indices `[0, k)` are data fragments, `[k, k+n)` are parity.
 */
class EncodeErasureFragmentsUseCase @Inject constructor(
    private val codec: ErasureCodec,
) {

    suspend operator fun invoke(
        file: File,
        params: ErasureParameters = ErasureParameters(),
    ): Result<List<ErasureFragment>> = withContext(Dispatchers.Default) {
        runCatching {
            require(params.k >= 1 && params.n >= 1 && params.k + params.n <= 255) {
                "GF(256) constraint: k >= 1, n >= 1, k + n <= 255 (got k=${params.k}, n=${params.n})"
            }
            require(file.exists() && file.isFile) { "File not found or not a regular file: $file" }

            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            val originalSize = bytes.size.toLong()
            require(originalSize > 0) { "File is empty, cannot encode" }

            val fragmentSize = (bytes.size + params.k - 1) / params.k
            require(fragmentSize.toLong() * params.k <= Int.MAX_VALUE) {
                "Padded size overflows Int (fragmentSize=$fragmentSize, k=${params.k})"
            }
            val paddedSize = fragmentSize * params.k
            val padded = bytes.copyOf(paddedSize)

            val dataBlocks = List(params.k) { i ->
                padded.copyOfRange(i * fragmentSize, (i + 1) * fragmentSize)
            }
            val parityBlocks = codec.encode(dataBlocks, params.k, params.n)

            val dataFragments = dataBlocks.mapIndexed { i, data ->
                ErasureFragment(index = i, isParity = false, data = data, originalFileSize = originalSize)
            }
            val parityFragments = parityBlocks.mapIndexed { i, data ->
                ErasureFragment(
                    index = params.k + i,
                    isParity = true,
                    data = data,
                    originalFileSize = originalSize,
                )
            }
            dataFragments + parityFragments
        }
    }
}
