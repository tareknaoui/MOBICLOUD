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
    // P-A9 — ip_address et port également préservés quand source est maintenue à LAN_MULTICAST :
    // évite de stocker une adresse relais sur un pair marqué LAN, ce qui rendrait la connexion TCP directe impossible.
    // BUG-FIX 2026-05-09 — IP/port également préservés si l'appelant passe NULL (ex: ProcessIncomingElectionEventUseCase
    // lors d'un COORDINATOR Bully ne connaît pas l'IP/port du sender, il ne fait que mettre à jour isSuperPair).
    // Sans cette protection, le DAO écrasait l'IP/port à NULL → le super-pair devenait injoignable pour
    // BlockSender → fragments perdus en upload.
    // display_name : COALESCE — si le nouveau nom est non-null, on l'applique ; sinon on conserve l'existant.
    @Query("""INSERT OR REPLACE INTO peer_nodes
        (node_id, public_key_bytes, reliability_score, ip_address, port, last_seen_timestamp_ms, is_active, source, is_super_pair, free_storage_bytes, display_name)
        VALUES (:nodeId, :publicKeyBytes, :reliabilityScore,
        CASE
            WHEN :ipAddress IS NULL THEN (SELECT ip_address FROM peer_nodes WHERE node_id = :nodeId)
            WHEN COALESCE((SELECT source FROM peer_nodes WHERE node_id = :nodeId), '') = 'LAN_MULTICAST'
                 AND :source != 'LAN_MULTICAST' THEN (SELECT ip_address FROM peer_nodes WHERE node_id = :nodeId)
            ELSE :ipAddress
        END,
        CASE
            WHEN :port IS NULL THEN (SELECT port FROM peer_nodes WHERE node_id = :nodeId)
            WHEN COALESCE((SELECT source FROM peer_nodes WHERE node_id = :nodeId), '') = 'LAN_MULTICAST'
                 AND :source != 'LAN_MULTICAST' THEN (SELECT port FROM peer_nodes WHERE node_id = :nodeId)
            ELSE :port
        END,
        :timestampMs, 1,
        CASE WHEN COALESCE((SELECT source FROM peer_nodes WHERE node_id = :nodeId), '') = 'LAN_MULTICAST'
             AND :source != 'LAN_MULTICAST' THEN 'LAN_MULTICAST' ELSE :source END,
        MAX(:isSuperPair, COALESCE((SELECT is_super_pair FROM peer_nodes WHERE node_id = :nodeId), 0)),
        :freeStorageBytes,
        COALESCE(:displayName, (SELECT display_name FROM peer_nodes WHERE node_id = :nodeId)))""")
    suspend fun insertOrUpdatePreservingRole(
        nodeId: String,
        publicKeyBytes: ByteArray,
        reliabilityScore: Float,
        ipAddress: String?,
        port: Int?,
        timestampMs: Long,
        source: String,
        isSuperPair: Int,
        freeStorageBytes: Long,
        displayName: String?
    )

    @Query("UPDATE peer_nodes SET is_active = 0 WHERE last_seen_timestamp_ms < :cutoffMs")
    suspend fun markInactive(cutoffMs: Long)

    @Query("UPDATE peer_nodes SET is_super_pair = 0 WHERE node_id = :nodeId")
    suspend fun clearSuperPairStatus(nodeId: String)

    @Query("DELETE FROM peer_nodes")
    suspend fun deleteAll()

    @Query("SELECT * FROM peer_nodes")
    fun getAllPeers(): Flow<List<PeerNodeEntity>>
}
