package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "node_identity")
data class NodeIdentityEntity(
    @PrimaryKey
    @ColumnInfo(name = "node_id")
    val nodeId: String,

    @ColumnInfo(name = "public_key_bytes")
    val publicKeyBytes: ByteArray,
    
    @ColumnInfo(name = "reliability_score")
    val reliabilityScore: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeIdentityEntity

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
