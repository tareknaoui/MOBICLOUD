package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

/**
 * Représente l'identité d'un noeud dans le réseau MobiCloud.
 * Cette classe est sérialisable pour pouvoir être échangée via Protobuf.
 */
@Serializable
data class NodeIdentity(
    val publicId: String,
    val publicKeyBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeIdentity

        if (publicId != other.publicId) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicId.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        return result
    }
}
