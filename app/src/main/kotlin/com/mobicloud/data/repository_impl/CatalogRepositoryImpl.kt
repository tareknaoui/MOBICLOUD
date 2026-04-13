package com.mobicloud.data.repository_impl

import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.FragmentLocationEntity
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.CalculateDhtRangeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Impl\u00e9mentation de [CatalogRepository] respectant les guardrails Clean Architecture :
 * - AC #6 : filtre DHT activ\u00e9 sur [insertEntry] via [CalculateDhtRangeUseCase].
 * - AC #9 : toutes les op\u00e9rations suspendues s'ex\u00e9cutent sur [Dispatchers.IO].
 * - AC #9 : toutes les op\u00e9rations retournent [Result]<T> pour une gestion explicite des erreurs.
 *
 * Convention de mapping :
 * - [CatalogEntry] ↔ [CatalogEntryEntity] + [List]<[FragmentLocationEntity]>
 * - [FragmentLocation.nodeIds] (List<String>) ↔ [FragmentLocationEntity.nodeIds] (String JSON)
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
            versionClock = versionClock
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
            fragmentLocations = fragmentLocations.map { it.toDomain() }
        )

    // --- CatalogRepository impl ---

    /**
     * Ins\u00e8re l'entr\u00e9e si et seulement si son [CatalogEntry.fileHash] est dans la partition DHT.
     * Sinon, l'op\u00e9ration retourne [Result.success] silencieusement (AC #6).
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
}
