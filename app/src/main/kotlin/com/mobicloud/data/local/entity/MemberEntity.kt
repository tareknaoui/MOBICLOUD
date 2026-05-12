package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cluster_members",
    indices = [
        Index(value = ["cluster_id", "status", "last_seen"], name = "idx_cluster_members_active_scan"),
        Index(value = ["status"], name = "idx_cluster_members_status")
    ]
)
data class MemberEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "cluster_id") val clusterId: String,
    @ColumnInfo(name = "public_key_bytes") val publicKeyBytes: ByteArray,
    @ColumnInfo(name = "ip_address") val ipAddress: String,
    @ColumnInfo(name = "port") val port: Int,
    @ColumnInfo(name = "free_bytes") val freeBytes: Long,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "status") val status: String = "ACTIVE"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemberEntity) return false
        return nodeId == other.nodeId
            && clusterId == other.clusterId
            && publicKeyBytes.contentEquals(other.publicKeyBytes)
            && ipAddress == other.ipAddress
            && port == other.port
            && freeBytes == other.freeBytes
            && lastSeen == other.lastSeen
            && role == other.role
            && status == other.status
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + clusterId.hashCode()
        result = 31 * result + publicKeyBytes.contentHashCode()
        result = 31 * result + ipAddress.hashCode()
        result = 31 * result + port
        result = 31 * result + freeBytes.hashCode()
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}
