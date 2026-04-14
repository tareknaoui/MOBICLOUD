package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer

@Entity(tableName = "peer_nodes")
data class PeerNodeEntity(
    @PrimaryKey val nodeId: String,
    @ColumnInfo(name = "public_key_bytes") val publicKeyBytes: ByteArray,
    @ColumnInfo(name = "reliability_score") val reliabilityScore: Float,
    @ColumnInfo(name = "ip_address") val ipAddress: String?,
    val port: Int?,
    @ColumnInfo(name = "last_seen_timestamp_ms") val lastSeenTimestampMs: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    val source: String = "LOCAL_UDP"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PeerNodeEntity
        if (nodeId != other.nodeId) return false
        if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        return result
    }
}

fun PeerNodeEntity.toDomain() = Peer(
    identity = NodeIdentity(nodeId, publicKeyBytes, reliabilityScore),
    lastSeenTimestampMs = lastSeenTimestampMs,
    // P5: valueOf() protégé — valeur inconnue en base retombe sur LOCAL_UDP sans crasher le StateFlow
    source = runCatching { DiscoverySource.valueOf(source) }.getOrDefault(DiscoverySource.LOCAL_UDP),
    ipAddress = ipAddress,
    port = port,
    isActive = isActive
)

fun Peer.toEntity() = PeerNodeEntity(
    nodeId = identity.nodeId,
    publicKeyBytes = identity.publicKeyBytes,
    reliabilityScore = identity.reliabilityScore,
    ipAddress = ipAddress,
    port = port,
    lastSeenTimestampMs = lastSeenTimestampMs,
    isActive = isActive,
    source = source.name
)
