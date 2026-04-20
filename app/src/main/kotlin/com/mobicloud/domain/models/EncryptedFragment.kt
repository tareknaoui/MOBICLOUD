package com.mobicloud.domain.models

data class EncryptedFragment(
    val index: Int,
    val isParity: Boolean,
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val originalFileSize: Long
) {
    init {
        require(index >= 0) { "index must be >= 0, got $index" }
        require(iv.size == 12) { "iv must be 12 bytes (96-bit), got ${iv.size}" }
        require(ciphertext.isNotEmpty()) { "ciphertext must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedFragment) return false
        return index == other.index &&
                isParity == other.isParity &&
                ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv) &&
                originalFileSize == other.originalFileSize
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + isParity.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + originalFileSize.hashCode()
        return result
    }
}
