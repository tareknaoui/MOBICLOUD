package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
data class JoinRequest(
    val senderNodeId: ByteArray,
    val candidatePublicKey: ByteArray,
    val freeBytes: Long,
    val reliabilityScore: Float,
    val timestampMs: Long,
    val signatureBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as JoinRequest
        if (!senderNodeId.contentEquals(other.senderNodeId)) return false
        if (!candidatePublicKey.contentEquals(other.candidatePublicKey)) return false
        if (freeBytes != other.freeBytes) return false
        if (reliabilityScore != other.reliabilityScore) return false
        if (timestampMs != other.timestampMs) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.contentHashCode()
        result = 31 * result + candidatePublicKey.contentHashCode()
        result = 31 * result + freeBytes.hashCode()
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
