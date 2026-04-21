package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BlockTransferMessage(
    @ProtoNumber(1) val blockId: String,
    @ProtoNumber(2) val ownerId: String,
    @ProtoNumber(3) val fragmentIndex: Int,
    @ProtoNumber(4) val isParity: Boolean,
    @ProtoNumber(5) val ciphertext: ByteArray,
    @ProtoNumber(6) val iv: ByteArray,
    @ProtoNumber(7) val originalFileSize: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockTransferMessage) return false
        return blockId == other.blockId &&
                ownerId == other.ownerId &&
                fragmentIndex == other.fragmentIndex &&
                isParity == other.isParity &&
                ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv) &&
                originalFileSize == other.originalFileSize
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + isParity.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + originalFileSize.hashCode()
        return result
    }
}
