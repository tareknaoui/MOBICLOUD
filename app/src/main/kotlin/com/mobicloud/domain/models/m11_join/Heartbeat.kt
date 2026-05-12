package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
data class Heartbeat(
    val senderNodeId: ByteArray,
    val freeBytes: Long,
    val ipAddress: String,
    val port: Int,
    val timestampMs: Long,
    val signatureBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Heartbeat
        if (!senderNodeId.contentEquals(other.senderNodeId)) return false
        if (freeBytes != other.freeBytes) return false
        if (ipAddress != other.ipAddress) return false
        if (port != other.port) return false
        if (timestampMs != other.timestampMs) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.contentHashCode()
        result = 31 * result + freeBytes.hashCode()
        result = 31 * result + ipAddress.hashCode()
        result = 31 * result + port
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
