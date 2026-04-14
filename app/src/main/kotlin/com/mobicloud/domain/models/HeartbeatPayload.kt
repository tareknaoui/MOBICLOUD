package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

/**
 * Payload sérialisé en Protobuf et diffusé via UDP Multicast.
 * Contient les informations nécessaires à l'identification du nœud
 * et à l'établissement d'une connexion TCP entrante (tcpPort).
 */
@Serializable
data class HeartbeatPayload(
    val nodeId: String,
    val publicKeyBytes: ByteArray,
    val reliabilityScore: Float = 1.0f,
    val tcpPort: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HeartbeatPayload
        if (nodeId != other.nodeId) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
        if (reliabilityScore != other.reliabilityScore) return false
        if (tcpPort != other.tcpPort) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + tcpPort
        return result
    }
}
