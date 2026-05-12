package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class HelloPayload(
    val nodeId: String,
    val publicKeyBytes: ByteArray,
    val tcpPort: Int,
    val reliabilityScore: Float,
    val freeStorageBytes: Long = 0L,
    // Story 11.1 — champs optionnels GPS + statut Super-Pair.
    // Defaults garantissent la rétrocompatibilité avec les anciens nœuds qui n'envoient
    // pas ces champs (kotlinx.serialization Protobuf ignore les champs inconnus).
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val superPair: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HelloPayload
        if (nodeId != other.nodeId) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
        if (tcpPort != other.tcpPort) return false
        if (reliabilityScore != other.reliabilityScore) return false
        if (freeStorageBytes != other.freeStorageBytes) return false
        if (gpsLatitude != other.gpsLatitude) return false
        if (gpsLongitude != other.gpsLongitude) return false
        if (superPair != other.superPair) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        result = 31 * result + tcpPort
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + freeStorageBytes.hashCode()
        result = 31 * result + gpsLatitude.hashCode()
        result = 31 * result + gpsLongitude.hashCode()
        result = 31 * result + superPair.hashCode()
        return result
    }
}
