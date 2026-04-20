package com.mobicloud.domain.models

/**
 * Configuration of a Reed-Solomon Erasure Coding pass.
 *
 * Defaults match the MVP profile: K=4 data blocks + N=2 parity blocks, tolerating the loss of
 * any 2 fragments out of 6. Changing [k] or [n] requires `k + n <= 255` (GF(256) constraint).
 *
 * @property k number of data blocks (>= 1).
 * @property n number of parity blocks (>= 1).
 * @property blockSize preferred block size in bytes. Used as an upper bound for internal chunking
 *                     of large files; for MVP, fragments are sized as `ceil(fileSize / k)`.
 */
data class ErasureParameters(
    val k: Int = 4,
    val n: Int = 2,
    val blockSize: Int = 1 * 1024 * 1024,
)
