package com.mobicloud.data.repository

import android.content.Context
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
import org.junit.Before
import org.junit.Test

class NodeSettingsRepositoryImplTest {

    private lateinit var dao: NodeSettingsDao
    private lateinit var context: Context
    private lateinit var repository: NodeSettingsRepositoryImpl

    @Before
    fun setup() {
        dao = mockk()
        context = mockk(relaxed = true)
        repository = NodeSettingsRepositoryImpl(dao, context)
    }

    @Test
    fun `getSettings retourne entity persistee quand DAO non vide`() = runTest {
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 4_000_000_000L, clusterId = "my-cluster")
        coEvery { dao.getSettings() } returns existingEntity

        val result = repository.getSettings()

        assertEquals("my-cluster", result.clusterId)
        assertEquals(4_000_000_000L, result.allocatedStorageBytes)
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
        coEvery { dao.getSettings() } returns null

        repository.updateClusterId("   ")

        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `clearClusterId vide le clusterId dans la DB`() = runTest {
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = "cid")
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns existingEntity
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        repository.clearClusterId()

        assertEquals("", upsertSlot.captured.clusterId)
        assertEquals(1_000_000L, upsertSlot.captured.allocatedStorageBytes)
    }

    @Test
    fun `clearClusterId no-op si aucune entite en DB`() = runTest {
        coEvery { dao.getSettings() } returns null

        repository.clearClusterId()

        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `updateAllocatedStorage persiste la nouvelle valeur`() = runTest {
        val existingEntity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 1_000_000L, clusterId = "cid")
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns existingEntity
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
    fun `updateAllocatedStorage preserve clusterId vide quand aucune entite`() = runTest {
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.getSettings() } returns null
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        repository.updateAllocatedStorage(2_000_000L)

        assertEquals("", upsertSlot.captured.clusterId)
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
