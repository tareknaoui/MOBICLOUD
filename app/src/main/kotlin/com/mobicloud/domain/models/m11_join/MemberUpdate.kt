package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
enum class MemberUpdateEvent { JOINED, LEFT }

@Serializable
data class MemberUpdate(
    val event: MemberUpdateEvent,
    val member: MemberInfo?,
    val leftNodeId: ByteArray?,
    val timestampMs: Long,
    val signatureBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MemberUpdate
        if (event != other.event) return false
        if (member != other.member) return false
        if (leftNodeId != null && other.leftNodeId != null) {
            if (!leftNodeId.contentEquals(other.leftNodeId)) return false
        } else if (leftNodeId != other.leftNodeId) return false
        if (timestampMs != other.timestampMs) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = event.hashCode()
        result = 31 * result + (member?.hashCode() ?: 0)
        result = 31 * result + (leftNodeId?.contentHashCode() ?: 0)
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
