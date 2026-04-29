package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class HelloMessage(
    val payload: HelloPayload,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HelloMessage
        if (payload != other.payload) return false
        if (!signature.contentEquals(other.signature)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = payload.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
