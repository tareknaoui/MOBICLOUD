package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mobicloud.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(member: MemberEntity)

    /** H17 : findByNodeId filtre status=ACTIVE pour éviter le routing vers un membre EVICTED. */
    @Query("SELECT * FROM cluster_members WHERE node_id = :nodeId AND status = 'ACTIVE' LIMIT 1")
    suspend fun findByNodeId(nodeId: String): MemberEntity?

    /** Variante sans filtre status — utilisée par `purge`/`debug` où l'on veut TOUTES les lignes. */
    @Query("SELECT * FROM cluster_members WHERE node_id = :nodeId LIMIT 1")
    suspend fun findByNodeIdAnyStatus(nodeId: String): MemberEntity?

    @Query("SELECT * FROM cluster_members WHERE cluster_id = :clusterId AND status = 'ACTIVE' ORDER BY last_seen DESC")
    fun listByClusterId(clusterId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM cluster_members WHERE cluster_id = :clusterId AND status = 'ACTIVE'")
    suspend fun listActiveSnapshot(clusterId: String): List<MemberEntity>

    /**
     * H14 : `AND status = 'ACTIVE'` empêche un HB tardif de "ressusciter à moitié" une ligne EVICTED
     * (lastSeen rafraîchi mais status reste EVICTED → ghost row invisible mais persistante).
     */
    @Query("""
        UPDATE cluster_members
           SET last_seen = :lastSeen, free_bytes = :freeBytes, ip_address = :ip, port = :port
         WHERE node_id = :nodeId AND status = 'ACTIVE'
    """)
    suspend fun touchHeartbeat(nodeId: String, lastSeen: Long, freeBytes: Long, ip: String, port: Int): Int

    /** M19 : compare-and-swap implicite via `last_seen < :cutoff` pour éviter d'évincer un membre vivant. */
    @Query("UPDATE cluster_members SET status = 'EVICTED' WHERE node_id = :nodeId AND last_seen < :cutoffMs")
    suspend fun markEvictedIfStale(nodeId: String, cutoffMs: Long): Int

    @Query("UPDATE cluster_members SET status = 'EVICTED' WHERE node_id = :nodeId")
    suspend fun markEvicted(nodeId: String): Int

    @Query("DELETE FROM cluster_members WHERE node_id = :nodeId AND cluster_id = :clusterId")
    suspend fun deleteByNodeId(nodeId: String, clusterId: String): Int

    /** Variante non-scopée (cleanup global). Préférer `deleteByNodeId(nodeId, clusterId)`. */
    @Query("DELETE FROM cluster_members WHERE node_id = :nodeId")
    suspend fun deleteByNodeIdAnyCluster(nodeId: String): Int

    /**
     * H18 : purge avec TTL distincte selon status.
     * - ACTIVE : 24 h (cleanup général)
     * - EVICTED : 1 h (rétention courte pour récupération éventuelle, spec AC2)
     */
    @Query("""
        DELETE FROM cluster_members
         WHERE (status = 'ACTIVE'  AND last_seen < :activeCutoffMs)
            OR (status = 'EVICTED' AND last_seen < :evictedCutoffMs)
    """)
    suspend fun purgeStale(activeCutoffMs: Long, evictedCutoffMs: Long): Int

    /** Scope un purge à un clusterId précis (changement de cluster, debug). */
    @Query("""
        DELETE FROM cluster_members
         WHERE cluster_id = :clusterId
           AND ((status = 'ACTIVE'  AND last_seen < :activeCutoffMs)
             OR (status = 'EVICTED' AND last_seen < :evictedCutoffMs))
    """)
    suspend fun purgeStaleInCluster(clusterId: String, activeCutoffMs: Long, evictedCutoffMs: Long): Int

    /**
     * @deprecated Préférer [purgeStale] avec TTL différenciée. Conservé pour compatibilité tests existants.
     * Applique la même TTL aux deux statuts → conflate la rétention EVICTED courte avec ACTIVE longue.
     */
    @Deprecated("Use purgeStale with separate TTLs (H18)", ReplaceWith("purgeStale(cutoffMs, cutoffMs)"))
    @Query("DELETE FROM cluster_members WHERE last_seen < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long): Int
}
