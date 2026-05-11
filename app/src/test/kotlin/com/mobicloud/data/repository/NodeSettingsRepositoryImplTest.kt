package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.NodeSettingsDao
import com.mobicloud.data.local.entity.NodeSettingsEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TEN_GB = 10L * 1024 * 1024 * 1024
private const val TWO_GB = 2L * 1024 * 1024 * 1024
private val UUID_V4_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

class NodeSettingsRepositoryImplTest {

    private lateinit var dao: NodeSettingsDao
    private lateinit var repository: NodeSettingsRepositoryImpl

    @Before
    fun setup() {
        dao = mockk()
        repository = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { java.util.UUID.randomUUID().toString() }
        )
    }

    @Test
    fun `getSettings insere valeur par defaut quand table vide`() = runTest {
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns null
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val result = repository.getSettings()

        val expected = (TEN_GB * 0.20).toLong()
        assertEquals(minOf(TWO_GB, expected), result.allocatedStorageBytes)
        coVerify(atLeast = 1) { dao.upsert(any()) }
        assertEquals(0, upsertSlot.captured.id)
    }

    @Test
    fun `getSettings ecrit clusterId UUID v4 quand provider retourne un id non vide`() = runTest {
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns null
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val result = repository.getSettings()

        assertTrue(
            "clusterId doit être un UUID v4 valide, reçu: ${result.clusterId}",
            UUID_V4_REGEX.matches(result.clusterId)
        )
    }

    @Test
    fun `getSettings ecrit clusterId quand entity existante a clusterId vide et provider non vide`() = runTest {
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = "")
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val result = repository.getSettings()

        assertTrue(
            "clusterId doit être un UUID v4 valide, reçu: ${result.clusterId}",
            UUID_V4_REGEX.matches(result.clusterId)
        )
        coVerify { dao.upsert(any()) }
    }

    // Si le SSID n'est pas lisible (permission refusee, hors WiFi), le provider retourne ""
    // -- on N'ECRIT PAS un clusterId vide en DB pour permettre un retry plus tard via
    // refreshClusterIdFromWifi(), au lieu de figer un UUID random a vie.
    @Test
    fun `getSettings ne persiste pas clusterId quand provider retourne vide`() = runTest {
        val repoEmpty = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { "" }
        )
        coEvery { dao.getSettings() } returns null
        coEvery { dao.upsert(any()) } returns Unit

        val result = repoEmpty.getSettings()

        assertEquals("", result.clusterId)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `refreshClusterIdFromWifi ecrit nouveau clusterId quand SSID disponible et entity vide`() = runTest {
        val newId = "11111111-2222-4333-8444-555555555555"
        val repoFixed = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { newId }
        )
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = "")
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val result = repoFixed.refreshClusterIdFromWifi()

        assertEquals(newId, result)
        assertEquals(newId, upsertSlot.captured.clusterId)
        assertEquals(1_000_000L, upsertSlot.captured.allocatedStorageBytes)
    }

    @Test
    fun `refreshClusterIdFromWifi ne touche pas DB quand SSID indisponible`() = runTest {
        val repoEmpty = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { "" }
        )
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = "abc")
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(any()) } returns Unit

        val result = repoEmpty.refreshClusterIdFromWifi()

        assertEquals("abc", result)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `refreshClusterIdFromWifi met a jour quand SSID change`() = runTest {
        val newId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        val repoFixed = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { newId }
        )
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = "old-cluster-id")
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val result = repoFixed.refreshClusterIdFromWifi()

        assertEquals(newId, result)
        assertEquals(newId, upsertSlot.captured.clusterId)
    }

    @Test
    fun `refreshClusterIdFromWifi no-op quand SSID donne le meme clusterId`() = runTest {
        val sameId = "550e8400-e29b-41d4-a716-446655440000"
        val repoFixed = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { sameId }
        )
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = sameId)
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(any()) } returns Unit

        val result = repoFixed.refreshClusterIdFromWifi()

        assertEquals(sameId, result)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    // Hors WiFi (clusterIdProvider == "") : getSettings conserve le clusterId persiste.
    @Test
    fun `getSettings retourne meme clusterId si deja persiste hors WiFi`() = runTest {
        val persistedClusterId = "550e8400-e29b-41d4-a716-446655440000"
        val repoOffWifi = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { "" }
        )
        val existingEntity = NodeSettingsEntity(
            id = 0,
            allocatedStorageBytes = 5L * 1024 * 1024 * 1024,
            clusterId = persistedClusterId
        )
        coEvery { dao.getSettings() } returns existingEntity

        val result = repoOffWifi.getSettings()

        assertEquals(persistedClusterId, result.clusterId)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `updateAllocatedStorage persiste la nouvelle valeur`() = runTest {
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns null
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val newBytes = 3L * 1024 * 1024 * 1024
        repository.updateAllocatedStorage(newBytes)

        coVerify { dao.upsert(any()) }
        assertEquals(newBytes, upsertSlot.captured.allocatedStorageBytes)
        assertEquals(0, upsertSlot.captured.id)
    }

    @Test
    fun `updateAllocatedStorage preserve le clusterId existant`() = runTest {
        val existingClusterId = "550e8400-e29b-41d4-a716-446655440000"
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = existingClusterId)
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateAllocatedStorage(2_000_000L)

        assertEquals(existingClusterId, upsertSlot.captured.clusterId)
    }

    // En WiFi avec clusterId persiste deja aligne sur le SSID : pas d'upsert.
    @Test
    fun `getSettings retourne valeur persistee sans ecraser quand alignee au SSID`() = runTest {
        val persistedClusterId = "550e8400-e29b-41d4-a716-446655440000"
        val repoAligned = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { persistedClusterId }
        )
        val persisted = NodeSettingsEntity(
            id = 0,
            allocatedStorageBytes = 5L * 1024 * 1024 * 1024,
            clusterId = persistedClusterId
        )
        coEvery { dao.getSettings() } returns persisted

        val result = repoAligned.getSettings()

        assertEquals(persisted.allocatedStorageBytes, result.allocatedStorageBytes)
        assertEquals(persistedClusterId, result.clusterId)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    // FIX SPLIT-CLUSTER (regression guard) : si la DB contient un clusterId d'une
    // session WiFi precedente et que le SSID courant en derive un different,
    // getSettings ECRASE la valeur stale. Sans ce comportement, deux telephones
    // sur le meme WiFi peuvent finir avec des clusterIds divergents -> split.
    @Test
    fun `getSettings ecrase clusterId stale en DB quand SSID actuel different`() = runTest {
        val staleClusterId = "11111111-2222-4333-8444-555555555555"
        val currentSsidClusterId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        val repoOnNewWifi = NodeSettingsRepositoryImpl(
            dao,
            freeSpaceProvider = { TEN_GB },
            clusterIdProvider = { currentSsidClusterId }
        )
        val staleEntity = NodeSettingsEntity(
            id = 0,
            allocatedStorageBytes = 5L * 1024 * 1024 * 1024,
            clusterId = staleClusterId
        )
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns staleEntity
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val result = repoOnNewWifi.getSettings()

        assertEquals(currentSsidClusterId, result.clusterId)
        assertEquals(currentSsidClusterId, upsertSlot.captured.clusterId)
        assertEquals(5L * 1024 * 1024 * 1024, upsertSlot.captured.allocatedStorageBytes)
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `updateClusterId preserve allocatedStorageBytes`() = runTest {
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 5_000_000_000L, clusterId = "old-cluster-id")
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateClusterId("new-cluster-id")

        coVerify { dao.upsert(any()) }
        assertEquals(5_000_000_000L, upsertSlot.captured.allocatedStorageBytes)
        assertEquals("new-cluster-id", upsertSlot.captured.clusterId)
    }

    @Test
    fun `updateClusterId no-op si id blank`() = runTest {
        repository.updateClusterId("   ")
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `observeSettings emet NodeSettings avec valeur par defaut si null`() = runTest {
        every { dao.observeSettings() } returns flowOf(null)

        val result = repository.observeSettings().first()

        val expectedDefault = minOf(TWO_GB, (TEN_GB * 0.20).toLong())
        assertEquals(expectedDefault, result.allocatedStorageBytes)
    }

    @Test
    fun `observeSettings emet valeur persistee depuis DAO`() = runTest {
        val clusterId = "550e8400-e29b-41d4-a716-446655440000"
        val entity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 4L * 1024 * 1024 * 1024, clusterId = clusterId)
        every { dao.observeSettings() } returns flowOf(entity)

        val result = repository.observeSettings().first()

        assertEquals(entity.allocatedStorageBytes, result.allocatedStorageBytes)
        assertEquals(clusterId, result.clusterId)
    }
}
