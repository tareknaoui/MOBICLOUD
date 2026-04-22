package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Story 6.2 — réponse contenant le ciphertext d'un bloc demandé.
 *
 * Override `equals`/`hashCode` requis car [ciphertext] est un `ByteArray`
 * (pattern établi par BlockTransferMessage).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BlockResponseMessage(
    @ProtoNumber(1) val blockId: String = "",
    @ProtoNumber(2) val fragmentIndex: Int = 0,
    @ProtoNumber(3) val isParity: Boolean = false,
    @ProtoNumber(4) val ciphertext: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockResponseMessage) return false
        return blockId == other.blockId &&
                fragmentIndex == other.fragmentIndex &&
                isParity == other.isParity &&
                ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + isParity.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
