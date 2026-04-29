package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mobicloud.data.local.entity.PeerNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Upsert
    suspend fun insertOrUpdate(peer: PeerNodeEntity)

    // Préserve is_super_pair si le nœud est déjà déclaré super-pair — évite que les heartbeats le réinitialisent à false.
    // DN2/AC7 — source LAN_MULTICAST est prioritaire : si le pair existe déjà en LAN_MULTICAST, une mise à jour
    // RELAY_HA ne dégrade pas la source (LAN direct > relais inter-réseau).
    @Query("""INSERT OR REPLACE INTO peer_nodes
        (node_id, public_key_bytes, reliability_score, ip_address, port, last_seen_timestamp_ms, is_active, source, is_super_pair)
        VALUES (:nodeId, :publicKeyBytes, :reliabilityScore, :ipAddress, :port, :timestampMs, 1,
        CASE WHEN COALESCE((SELECT source FROM peer_nodes WHERE node_id = :nodeId), '') = 'LAN_MULTICAST'
             AND :source != 'LAN_MULTICAST' THEN 'LAN_MULTICAST' ELSE :source END,
        MAX(:isSuperPair, COALESCE((SELECT is_super_pair FROM peer_nodes WHERE node_id = :nodeId), 0)))""")
    suspend fun insertOrUpdatePreservingRole(
        nodeId: String,
        publicKeyBytes: ByteArray,
        reliabilityScore: Float,
        ipAddress: String?,
        port: Int?,
        timestampMs: Long,
        source: String,
        isSuperPair: Int
    )

    @Query("UPDATE peer_nodes SET is_active = 0 WHERE last_seen_timestamp_ms < :cutoffMs")
    suspend fun markInactive(cutoffMs: Long)

    @Query("UPDATE peer_nodes SET is_super_pair = 0 WHERE node_id = :nodeId")
    suspend fun clearSuperPairStatus(nodeId: String)

    @Query("SELECT * FROM peer_nodes")
    fun getAllPeers(): Flow<List<PeerNodeEntity>>
}
