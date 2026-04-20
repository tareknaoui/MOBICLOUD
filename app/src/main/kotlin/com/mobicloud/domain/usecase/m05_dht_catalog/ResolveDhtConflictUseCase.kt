package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.TombstoneEntry
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.TombstoneRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveDhtConflictUseCase @Inject constructor(
    private val dhtRepository: DhtRepository,
    private val tombstoneRepository: TombstoneRepository
) {

    companion object {
        const val TOMBSTONE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Résout un conflit CRDT LWW lors d'une réception Gossip-Delta.
     * 1. Si un tombstone existe → ignorer (anti-résurrection, AC#5)
     * 2. Si aucune entrée locale → insérer directement
     * 3. LWW : timestamp distant > local → remplacer (AC#3)
     * 4. Tie-break : timestamps égaux → nodeId lexicographiquement supérieur gagne (AC#4)
     */
    suspend fun resolve(remote: DhtEntry): Result<Unit> {
        if (tombstoneRepository.existsForBlock(remote.blockId)) {
            return Result.success(Unit)
        }

        val local = dhtRepository.findByBlockId(remote.blockId)
            .getOrElse { return Result.failure(it) }

        return when {
            local == null -> dhtRepository.insertEntryWithTimestamp(
                remote.blockId, remote.nodeId, remote.ipAddress, remote.port, remote.timestamp
            )
            remote.timestamp > local.timestamp -> dhtRepository.insertEntryWithTimestamp(
                remote.blockId, remote.nodeId, remote.ipAddress, remote.port, remote.timestamp
            )
            remote.timestamp < local.timestamp -> Result.success(Unit)
            else -> {
                if (remote.nodeId > local.nodeId) {
                    dhtRepository.insertEntryWithTimestamp(
                        remote.blockId, remote.nodeId, remote.ipAddress, remote.port, remote.timestamp
                    )
                } else {
                    Result.success(Unit)
                }
            }
        }
    }

    /** Marque un bloc comme supprimé via TombstoneEntry CRDT (AC#5). */
    suspend fun tombstone(blockId: String): Result<Unit> =
        tombstoneRepository.insert(TombstoneEntry(blockId, System.currentTimeMillis()))

    /** Purge les tombstones expirés (âge > 24h) au démarrage du service (AC#6). */
    suspend fun purgeExpiredTombstones(): Result<Int> {
        val cutoff = System.currentTimeMillis() - TOMBSTONE_MAX_AGE_MS
        return tombstoneRepository.deleteOlderThan(cutoff)
    }
}
