package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
sealed class JoinResponse {
    @Serializable
    data class JoinAccept(
        val clusterId: String,
        val superPairNodeId: ByteArray,
        val memberSnapshot: List<MemberInfo>,
        val timestampMs: Long,
        val signatureBytes: ByteArray
    ) : JoinResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as JoinAccept
            if (clusterId != other.clusterId) return false
            if (!superPairNodeId.contentEquals(other.superPairNodeId)) return false
            if (memberSnapshot != other.memberSnapshot) return false
            if (timestampMs != other.timestampMs) return false
            if (!signatureBytes.contentEquals(other.signatureBytes)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = clusterId.hashCode()
            result = 31 * result + superPairNodeId.contentHashCode()
            result = 31 * result + memberSnapshot.hashCode()
            result = 31 * result + timestampMs.hashCode()
            result = 31 * result + signatureBytes.contentHashCode()
            return result
        }
    }

    @Serializable
    data class JoinRedirect(
        val reason: JoinRedirectReason,
        // P15 review : `distanceMeters` retiré Story 12.1 — résidu GPS code mort sur le wire.
        val alternativeSuperPeers: List<SuperPeerHint> = emptyList(),
        val timestampMs: Long,
        val signatureBytes: ByteArray
    ) : JoinResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as JoinRedirect
            if (reason != other.reason) return false
            if (alternativeSuperPeers != other.alternativeSuperPeers) return false
            if (timestampMs != other.timestampMs) return false
            if (!signatureBytes.contentEquals(other.signatureBytes)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = reason.hashCode()
            result = 31 * result + alternativeSuperPeers.hashCode()
            result = 31 * result + timestampMs.hashCode()
            result = 31 * result + signatureBytes.contentHashCode()
            return result
        }
    }
}
