package com.mobicloud.data.repository_impl

import android.util.Log
import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.FragmentLocationEntity
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.WrappedFileMasterKey
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.CalculateDhtRangeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implémentation de [CatalogRepository] respectant les guardrails Clean Architecture :
 * - AC #6 : filtre DHT activé sur [insertEntry] via [CalculateDhtRangeUseCase].
 * - AC #9 : toutes les opérations suspendues s'exécutent sur [Dispatchers.IO].
 * - AC #9 : toutes les opérations retournent [Result]<T> pour une gestion explicite des erreurs.
 *
 * Convention de mapping :
 * - [CatalogEntry] ↔ [CatalogEntryEntity] + [List]<[FragmentLocationEntity]>
 * - [FragmentLocation.nodeIds] (List<String>) ↔ [FragmentLocationEntity.nodeIds] (String JSON)
 * - [CatalogEntry.wrappedMasterKey] ↔ [CatalogEntryEntity.wrappedMasterKeyJson] (String JSON)
 */
class CatalogRepositoryImpl(
    private val catalogDao: CatalogDao,
    private val calculateDhtRange: CalculateDhtRangeUseCase,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : CatalogRepository {

    // --- Mappers ---

    private fun CatalogEntry.toEntity(): CatalogEntryEntity =
        CatalogEntryEntity(
            fileHash = fileHash,
            ownerPubKeyHash = ownerPubKeyHash,
            versionClock = versionClock,
            wrappedMasterKeyJson = wrappedMasterKey?.let {
                json.encodeToString(WrappedFileMasterKey.serializer(), it)
            },
            originalFileSize = originalFileSize,
            originalFileName = originalFileName,
            k = k,
            n = n,
            isInTrash = isInTrash,
            deletedAt = deletedAt,
            folderPath = folderPath
        )

    private fun FragmentLocation.toEntity(catalogFileHash: String): FragmentLocationEntity =
        FragmentLocationEntity(
            catalogFileHash = catalogFileHash,
            fragmentIndex = fragmentIndex,
            fragmentHash = fragmentHash,
            nodeIds = json.encodeToString(nodeIds)
        )

    private fun FragmentLocationEntity.toDomain(): FragmentLocation =
        FragmentLocation(
            fragmentIndex = fragmentIndex,
            fragmentHash = fragmentHash,
            nodeIds = try {
                json.decodeFromString(nodeIds)
            } catch (e: Exception) {
                emptyList()
            }
        )

    private fun com.mobicloud.data.local.entity.CatalogEntryWithFragments.toDomain(): CatalogEntry =
        CatalogEntry(
            fileHash = catalogEntry.fileHash,
            ownerPubKeyHash = catalogEntry.ownerPubKeyHash,
            versionClock = catalogEntry.versionClock,
            fragmentLocations = fragmentLocations.map { it.toDomain() },
            wrappedMasterKey = catalogEntry.wrappedMasterKeyJson?.let { jsonStr ->
                try {
                    json.decodeFromString(WrappedFileMasterKey.serializer(), jsonStr)
                } catch (e: Exception) {
                    Log.w("CatalogRepo", "Corrupted wrappedMasterKeyJson for ${catalogEntry.fileHash}: $e")
                    null
                }
            },
            originalFileSize = catalogEntry.originalFileSize,
            originalFileName = catalogEntry.originalFileName,
            k = catalogEntry.k,
            n = catalogEntry.n,
            isInTrash = catalogEntry.isInTrash,
            deletedAt = catalogEntry.deletedAt,
            folderPath = catalogEntry.folderPath
        )

    // --- CatalogRepository impl ---

    /**
     * Insère l'entrée si et seulement si son [CatalogEntry.fileHash] est dans la partition DHT.
     * Sinon, l'opération retourne [Result.success] silencieusement (AC #6).
     */
    override suspend fun insertEntry(
        entry: CatalogEntry,
        nodeId: String,
        successorId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val isInRange = calculateDhtRange(
                key = entry.fileHash,
                nodeId = nodeId,
                successorId = successorId
            )
            if (!isInRange) return@runCatching // Rejet silencieux (AC #6)

            // Story 13.5 — préserver le folderPath local lors d'un upsert Gossip :
            // l'entrée entrante n'a pas de folderPath (local uniquement), on lit l'existant en DB.
            val existingFolderPath = catalogDao.getCatalogEntry(entry.fileHash)?.catalogEntry?.folderPath
            val entityEntry = entry.copy(folderPath = existingFolderPath).toEntity()
            val entityFragments = entry.fragmentLocations.map { it.toEntity(entry.fileHash) }
            catalogDao.insertWithFragments(entityEntry, entityFragments)
        }
    }

    /**
     * Insère une entrée catalogue sans appliquer le filtre DHT.
     * Réservé au nœud propriétaire qui distribue ses propres blocs (Story 5.3).
     */
    override suspend fun insertOwnerEntry(entry: CatalogEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entityEntry = entry.toEntity()
                val entityFragments = entry.fragmentLocations.map { it.toEntity(entry.fileHash) }
                catalogDao.insertWithFragments(entityEntry, entityFragments)
            }
        }

    override suspend fun updateEntryOnly(entry: CatalogEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.updateCatalogEntryOnly(entry.toEntity()) }
        }

    override fun getEntryFlow(hash: String): Flow<CatalogEntry?> =
        catalogDao.getCatalogEntryFlow(hash).map { it?.toDomain() }

    override fun getAllEntriesFlow(): Flow<List<CatalogEntry>> =
        catalogDao.getAllCatalogEntriesFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getEntry(hash: String): Result<CatalogEntry?> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.getCatalogEntry(hash)?.toDomain() }
        }

    // Story 13.2 — corbeille

    override fun getActiveEntriesFlow(): Flow<List<CatalogEntry>> =
        catalogDao.getAllActiveEntriesFlow().map { list -> list.map { it.toDomain() } }

    override fun getDeletedEntriesFlow(): Flow<List<CatalogEntry>> =
        catalogDao.getDeletedEntriesFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun moveToTrash(fileHash: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.softDeleteEntry(fileHash, System.currentTimeMillis()) }
        }

    override suspend fun restoreFromTrash(fileHash: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.restoreEntry(fileHash) }
        }

    override suspend fun permanentlyDelete(fileHash: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.permanentlyDeleteEntry(fileHash) }
        }

    override suspend fun emptyTrash(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.emptyTrash() }
        }

    override suspend fun purgeExpired(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val expiryTs = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                catalogDao.purgeExpiredEntries(expiryTs)
            }
        }

    // Story 13.5 — dossiers locaux

    override suspend fun moveFileToFolder(fileHash: String, folderPath: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.updateFolderPath(fileHash, folderPath) }
        }

    override fun getActiveFolderNamesFlow(): Flow<List<String>> =
        catalogDao.getActiveFolderNamesFlow()

    override suspend fun renameFolder(oldPath: String, newPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.renameFolder(oldPath, newPath) }
        }

    override suspend fun deleteFolder(folderPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { catalogDao.moveFilesToRoot(folderPath) }
        }
}
