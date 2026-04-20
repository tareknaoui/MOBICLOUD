package com.mobicloud.domain.models

data class WrappedFileMasterKey(
    val ephemeralPublicKeyBytes: ByteArray,
    val iv: ByteArray,
    val encryptedKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WrappedFileMasterKey) return false
        return ephemeralPublicKeyBytes.contentEquals(other.ephemeralPublicKeyBytes) &&
                iv.contentEquals(other.iv) &&
                encryptedKey.contentEquals(other.encryptedKey)
    }

    override fun hashCode(): Int {
        var result = ephemeralPublicKeyBytes.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + encryptedKey.contentHashCode()
        return result
    }
}
