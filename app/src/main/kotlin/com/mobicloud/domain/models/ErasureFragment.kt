package com.mobicloud.domain.models

/**
 * A single erasure-coding fragment produced by a Reed-Solomon encoder.
 *
 * Fragments of the same original file share [originalFileSize], which is used on decode to
 * trim the trailing zero-padding introduced to align the file to a multiple of `k`.
 *
 * @property index position in the systematic (K+N)×K generator matrix: indices `[0, k)` are
 *                 data fragments (copies of the original data), indices `[k, k+n)` are parity.
 * @property isParity convenience flag: `index >= k`.
 * @property data raw bytes of this fragment, still in clear text at this stage (encryption is
 *                applied later in Story 5.2).
 * @property originalFileSize size of the original file before padding, in bytes.
 */
data class ErasureFragment(
    val index: Int,
    val isParity: Boolean,
    val data: ByteArray,
    val originalFileSize: Long,
) {
    init {
        require(index >= 0) { "index must be >= 0, got $index" }
        require(originalFileSize >= 0) { "originalFileSize must be >= 0, got $originalFileSize" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ErasureFragment) return false
        return index == other.index &&
                isParity == other.isParity &&
                originalFileSize == other.originalFileSize &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + isParity.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + originalFileSize.hashCode()
        return result
    }
}
