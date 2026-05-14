package com.mobicloud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobicloud.data.local.entity.HostedBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostedBlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHostedBlock(block: HostedBlockEntity)

    @Query("SELECT * FROM hosted_blocks WHERE block_id = :blockId")
    suspend fun getHostedBlock(blockId: String): HostedBlockEntity?

    @Query("SELECT * FROM hosted_blocks")
    fun getAllHostedBlocksFlow(): Flow<List<HostedBlockEntity>>

    @Query("DELETE FROM hosted_blocks WHERE block_id = :blockId")
    suspend fun deleteHostedBlock(blockId: String)

    // Fix P9 : ORDER BY garantit un ordre stable pour la signature du payload DEPARTURE_NOTICE
    @Query("SELECT block_id FROM hosted_blocks ORDER BY block_id ASC")
    suspend fun getAllBlockIds(): List<String>

    // Story 1.6 — somme des octets hébergés pour affichage quota settings
    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM hosted_blocks")
    fun observeTotalSizeBytes(): Flow<Long>

    // Story 9.2 — lecture one-shot de la même agrégation, pour calcul `freeBytes` lors du REGISTER_PEER
    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM hosted_blocks")
    suspend fun getTotalSizeBytes(): Long

    // Story 13.1 — KPI Dashboard "Fichiers protégés" : count des fragments hébergés.
    // Approximation grand public (chaque fragment ≈ part d'un fichier ; un utilisateur n'a pas
    // à connaître la distinction technique fragment / fichier dans le Mode Simple).
    @Query("SELECT COUNT(*) FROM hosted_blocks")
    fun observeHostedBlockCount(): Flow<Int>
}
