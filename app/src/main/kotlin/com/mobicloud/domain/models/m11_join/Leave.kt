package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
data class Leave(
    val senderNodeId: ByteArray,
    val timestampMs: Long,
    val signatureBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Leave
        if (!senderNodeId.contentEquals(other.senderNodeId)) return false
        if (timestampMs != other.timestampMs) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
