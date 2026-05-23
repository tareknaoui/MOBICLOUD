package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RevokeBlockMessage(
    @ProtoNumber(1) val blockId: String = "",
    @ProtoNumber(2) val ownerNodeId: String = "",
    @ProtoNumber(3) val ownerPublicKeyBytes: ByteArray = byteArrayOf(),
    @ProtoNumber(4) val signatureBytes: ByteArray = byteArrayOf(),
    @ProtoNumber(5) val timestampMs: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RevokeBlockMessage) return false
        return blockId == other.blockId &&
                ownerNodeId == other.ownerNodeId &&
                ownerPublicKeyBytes.contentEquals(other.ownerPublicKeyBytes) &&
                signatureBytes.contentEquals(other.signatureBytes) &&
                timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + ownerNodeId.hashCode()
        result = 31 * result + ownerPublicKeyBytes.contentHashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}
