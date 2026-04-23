package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DepartureNoticeMessage(
    @ProtoNumber(1) val senderNodeId: String = "",
    @ProtoNumber(2) val hostedBlockIds: List<String> = emptyList(),
    @ProtoNumber(3) val signatureBytes: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DepartureNoticeMessage
        if (senderNodeId != other.senderNodeId) return false
        if (hostedBlockIds != other.hostedBlockIds) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + hostedBlockIds.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
