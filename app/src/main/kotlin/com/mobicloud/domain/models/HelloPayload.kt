package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class HelloPayload(
    val nodeId: String,
    val publicKeyBytes: ByteArray,
    val tcpPort: Int,
    val reliabilityScore: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HelloPayload
        if (nodeId != other.nodeId) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
        if (tcpPort != other.tcpPort) return false
        if (reliabilityScore != other.reliabilityScore) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        result = 31 * result + tcpPort
        result = 31 * result + reliabilityScore.hashCode()
        return result
    }
}
