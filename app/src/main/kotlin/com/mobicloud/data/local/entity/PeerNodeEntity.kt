package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer

@Entity(tableName = "peer_nodes")
data class PeerNodeEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "public_key_bytes") val publicKeyBytes: ByteArray,
    @ColumnInfo(name = "reliability_score") val reliabilityScore: Float,
    @ColumnInfo(name = "ip_address") val ipAddress: String?,
    val port: Int?,
    @ColumnInfo(name = "last_seen_timestamp_ms") val lastSeenTimestampMs: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    val source: String = "REMOTE_FIREBASE",
    @ColumnInfo(name = "is_super_pair") val isSuperPair: Boolean = false,
    @ColumnInfo(name = "free_storage_bytes") val freeStorageBytes: Long = 0L,
    @ColumnInfo(name = "display_name") val displayName: String? = null
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
    // P5: valueOf() protégé — valeur inconnue en base retombe sur REMOTE_FIREBASE sans crasher le StateFlow
    source = runCatching { DiscoverySource.valueOf(source) }.getOrDefault(DiscoverySource.REMOTE_FIREBASE),
    ipAddress = ipAddress,
    port = port,
    isActive = isActive,
    isSuperPair = isSuperPair,
    freeStorageBytes = freeStorageBytes,
    displayName = displayName
)

fun Peer.toEntity() = PeerNodeEntity(
    nodeId = identity.nodeId,
    publicKeyBytes = identity.publicKeyBytes,
    reliabilityScore = identity.reliabilityScore,
    ipAddress = ipAddress,
    port = port,
    lastSeenTimestampMs = lastSeenTimestampMs,
    isActive = isActive,
    source = source.name,
    isSuperPair = isSuperPair,
    freeStorageBytes = freeStorageBytes,
    displayName = displayName
)
