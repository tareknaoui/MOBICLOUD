package com.mobicloud.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Snapshot du cluster côté membre, survit au crash (Story 11.3 AC11 / FR-11.8).
 *
 * `schema_version` permet d'invalider proprement le JSON si `MemberInfo` évolue
 * de manière incompatible. Tout snapshot avec version != courante DOIT être
 * ignoré et reseed via JOIN_ACCEPT ou MEMBER_UPDATE.
 */
@Entity(tableName = "member_snapshot")
data class MemberSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "cluster_id") val clusterId: String,
    @ColumnInfo(name = "super_pair_node_id_hex") val superPairNodeIdHex: String,
    @ColumnInfo(name = "last_updated_ms") val lastUpdatedMs: Long,
    @ColumnInfo(name = "members_json") val membersJson: String,
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = SCHEMA_VERSION
) {
    companion object {
        /** Incrémenter à chaque changement incompatible du format de `members_json`. */
        const val SCHEMA_VERSION = 1
    }
}
