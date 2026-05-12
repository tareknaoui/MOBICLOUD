package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.Serializable

@Serializable
data class MemberInfo(
    val nodeId: ByteArray,
    val publicKey: ByteArray,
    val ipAddress: String,
    val port: Int,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val freeBytes: Long,
    val role: MemberRole
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MemberInfo
        if (!nodeId.contentEquals(other.nodeId)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (ipAddress != other.ipAddress) return false
        if (port != other.port) return false
        if (gpsLatitude != other.gpsLatitude) return false
        if (gpsLongitude != other.gpsLongitude) return false
        if (freeBytes != other.freeBytes) return false
        if (role != other.role) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + ipAddress.hashCode()
        result = 31 * result + port
        result = 31 * result + (gpsLatitude?.hashCode() ?: 0)
        result = 31 * result + (gpsLongitude?.hashCode() ?: 0)
        result = 31 * result + freeBytes.hashCode()
        result = 31 * result + role.hashCode()
        return result
    }
}

@Serializable
enum class MemberRole { SUPER_PAIR, MEMBER }
