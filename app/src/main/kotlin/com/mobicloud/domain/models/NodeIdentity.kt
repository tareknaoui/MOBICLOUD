package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

/**
 * Représente l'identité d'un noeud dans le réseau MobiCloud.
 * Cette classe est sérialisable pour pouvoir être échangée via Protobuf.
 */
@Serializable
data class NodeIdentity(
    val nodeId: String,
    val publicKeyBytes: ByteArray,
    val reliabilityScore: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeIdentity

        if (nodeId != other.nodeId) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
        if (reliabilityScore != other.reliabilityScore) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        result = 31 * result + reliabilityScore.hashCode()
        return result
    }
}
