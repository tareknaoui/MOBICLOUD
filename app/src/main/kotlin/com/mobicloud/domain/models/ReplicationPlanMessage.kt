package com.mobicloud.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Story 7.3 — Plan de réplication envoyé par le Super-Pair à un donneur survivant
 * pour restaurer un bloc sous-répliqué. Contient une unique directive (pas un batch
 * comme MigrationPlanMessage) — réutilise MigrateBlockDirective.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ReplicationPlanMessage(
    @ProtoNumber(1) val superPeerNodeId: String = "",
    @ProtoNumber(2) val directive: MigrateBlockDirective = MigrateBlockDirective(),
    @ProtoNumber(3) val signatureBytes: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ReplicationPlanMessage
        if (superPeerNodeId != other.superPeerNodeId) return false
        if (directive != other.directive) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = superPeerNodeId.hashCode()
        result = 31 * result + directive.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        return result
    }
}
