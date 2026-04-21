package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WrappedFileMasterKey(
    @ProtoNumber(1) val ephemeralPublicKeyBytes: ByteArray,
    @ProtoNumber(2) val iv: ByteArray,
    @ProtoNumber(3) val encryptedKey: ByteArray
) {
    init {
        require(ephemeralPublicKeyBytes.isNotEmpty()) { "ephemeralPublicKeyBytes must not be empty" }
        require(iv.size == 12) { "iv must be 12 bytes (96-bit), got ${iv.size}" }
        require(encryptedKey.isNotEmpty()) { "encryptedKey must not be empty" }
    }

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
