package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

/**
 * Payload sérialisé en Protobuf pour le protocole d'élection Bully.
 */
@Serializable
data class ElectionPayload(
    val senderNodeId: String,
    val type: ElectionMessageType,
    val reliabilityScore: Float,
    val signatureBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ElectionPayload

        if (senderNodeId != other.senderNodeId) return false
        if (type != other.type) return false
        if (reliabilityScore != other.reliabilityScore) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
