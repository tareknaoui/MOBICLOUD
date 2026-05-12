package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
data class SuperPeerHint(
    val nodeId: ByteArray,
    val clusterId: String = "",
    val ipAddress: String,
    val port: Int,
    val reliabilityScore: Float = 0f,
    val currentMemberCount: Int = 0    // Story 12.1 — critère d'admission (load balancing)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SuperPeerHint
        if (!nodeId.contentEquals(other.nodeId)) return false
        if (clusterId != other.clusterId) return false
        if (ipAddress != other.ipAddress) return false
        if (port != other.port) return false
        if (reliabilityScore != other.reliabilityScore) return false
        if (currentMemberCount != other.currentMemberCount) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.contentHashCode()
        result = 31 * result + clusterId.hashCode()
        result = 31 * result + ipAddress.hashCode()
        result = 31 * result + port
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + currentMemberCount
        return result
    }
}
