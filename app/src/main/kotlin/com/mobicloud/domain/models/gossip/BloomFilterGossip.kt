package com.mobicloud.domain.models.gossip

import kotlinx.serialization.Serializable

@Serializable
data class BloomFilterGossip(
    val senderNodeId: String,
    val bloomFilterBytes: ByteArray,
    val bloomFilterSize: Int = 1024,
    val numHashFunctions: Int = 3,
    val partitionIds: List<String>,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BloomFilterGossip) return false
        return senderNodeId == other.senderNodeId &&
            bloomFilterBytes.contentEquals(other.bloomFilterBytes) &&
            bloomFilterSize == other.bloomFilterSize &&
            numHashFunctions == other.numHashFunctions &&
            partitionIds == other.partitionIds &&
            timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + bloomFilterBytes.contentHashCode()
        result = 31 * result + bloomFilterSize
        result = 31 * result + numHashFunctions
        result = 31 * result + partitionIds.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
