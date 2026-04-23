package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MigrateBlockDirective(
    @ProtoNumber(1) val blockId: String = "",
    @ProtoNumber(2) val destinationNodeId: String = "",
    @ProtoNumber(3) val destinationIp: String = "",
    @ProtoNumber(4) val destinationPort: Int = 0,
    @ProtoNumber(5) val destinationPublicKeyBytes: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MigrateBlockDirective
        if (blockId != other.blockId) return false
        if (destinationNodeId != other.destinationNodeId) return false
        if (destinationIp != other.destinationIp) return false
        if (destinationPort != other.destinationPort) return false
        if (!destinationPublicKeyBytes.contentEquals(other.destinationPublicKeyBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + destinationNodeId.hashCode()
        result = 31 * result + destinationIp.hashCode()
        result = 31 * result + destinationPort
        result = 31 * result + destinationPublicKeyBytes.contentHashCode()
        return result
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MigrationPlanMessage(
    @ProtoNumber(1) val superPeerNodeId: String = "",
    @ProtoNumber(2) val directives: List<MigrateBlockDirective> = emptyList(),
    @ProtoNumber(3) val signatureBytes: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MigrationPlanMessage
        if (superPeerNodeId != other.superPeerNodeId) return false
        if (directives != other.directives) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = superPeerNodeId.hashCode()
        result = 31 * result + directives.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
