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
import org.junit.Assert.assertNotEquals
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
    fun `getSettings genere clusterId UUID v4 valide au premier appel`() = runTest {
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
    fun `getSettings genere UUID quand entity existante a clusterId vide`() = runTest {
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

    @Test
    fun `getSettings retourne meme clusterId si deja persiste`() = runTest {
        val persistedClusterId = "550e8400-e29b-41d4-a716-446655440000"
        val existingEntity = NodeSettingsEntity(
            id = 0,
            allocatedStorageBytes = 5L * 1024 * 1024 * 1024,
            clusterId = persistedClusterId
        )
        coEvery { dao.getSettings() } returns existingEntity

        val result = repository.getSettings()

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

    @Test
    fun `getSettings retourne valeur persistee sans ecraser`() = runTest {
        val persistedClusterId = "550e8400-e29b-41d4-a716-446655440000"
        val persisted = NodeSettingsEntity(
            id = 0,
            allocatedStorageBytes = 5L * 1024 * 1024 * 1024,
            clusterId = persistedClusterId
        )
        coEvery { dao.getSettings() } returns persisted

        val result = repository.getSettings()

        assertEquals(persisted.allocatedStorageBytes, result.allocatedStorageBytes)
        assertEquals(persistedClusterId, result.clusterId)
        coVerify(exactly = 0) { dao.upsert(any()) }
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
