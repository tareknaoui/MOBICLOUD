package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** Fenetre anti-replay (±) pour les payloads signes Plan/Departure. */
const val PLAN_TIMESTAMP_WINDOW_MS: Long = 30_000L

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DepartureNoticeMessage(
    @ProtoNumber(1) val senderNodeId: String = "",
    @ProtoNumber(2) val hostedBlockIds: List<String> = emptyList(),
    @ProtoNumber(3) val signatureBytes: ByteArray = byteArrayOf(),
    @ProtoNumber(4) val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DepartureNoticeMessage
        if (senderNodeId != other.senderNodeId) return false
        if (hostedBlockIds != other.hostedBlockIds) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        if (timestampMs != other.timestampMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + hostedBlockIds.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}
