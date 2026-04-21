package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BlockAckMessage(
    @ProtoNumber(1) val blockId: String,
    @ProtoNumber(2) val blockHash: String,
    @ProtoNumber(3) val receiverNodeId: String,
    @ProtoNumber(4) val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockAckMessage) return false
        return blockId == other.blockId &&
                blockHash == other.blockHash &&
                receiverNodeId == other.receiverNodeId &&
                signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + blockHash.hashCode()
        result = 31 * result + receiverNodeId.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
