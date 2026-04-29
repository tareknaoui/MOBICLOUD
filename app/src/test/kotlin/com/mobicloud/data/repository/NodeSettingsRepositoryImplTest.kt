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
import org.junit.Before
import org.junit.Test

private const val TEN_GB = 10L * 1024 * 1024 * 1024
private const val TWO_GB = 2L * 1024 * 1024 * 1024

class NodeSettingsRepositoryImplTest {

    private lateinit var dao: NodeSettingsDao
    private lateinit var repository: NodeSettingsRepositoryImpl

    @Before
    fun setup() {
        dao = mockk()
        repository = NodeSettingsRepositoryImpl(dao, freeSpaceProvider = { TEN_GB })
    }

    @Test
    fun `getSettings insere valeur par defaut quand table vide`() = runTest {
        coEvery { dao.getSettings() } returns null
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val result = repository.getSettings()

        // default = min(2GB, 20% de 10GB) = min(2GB, 2GB) = 2GB
        val expected = (TEN_GB * 0.20).toLong()
        assertEquals(minOf(TWO_GB, expected), result.allocatedStorageBytes)
        coVerify { dao.upsert(any()) }
        assertEquals(0, upsertSlot.captured.id)
    }

    @Test
    fun `updateAllocatedStorage persiste la nouvelle valeur`() = runTest {
        val upsertSlot = slot<NodeSettingsEntity>()
        coEvery { dao.upsert(capture(upsertSlot)) } returns Unit

        val newBytes = 3L * 1024 * 1024 * 1024
        repository.updateAllocatedStorage(newBytes)

        coVerify { dao.upsert(any()) }
        assertEquals(newBytes, upsertSlot.captured.allocatedStorageBytes)
        assertEquals(0, upsertSlot.captured.id)
    }

    @Test
    fun `getSettings retourne valeur persistee sans ecraser`() = runTest {
        val persisted = NodeSettingsEntity(id = 0, allocatedStorageBytes = 5L * 1024 * 1024 * 1024)
        coEvery { dao.getSettings() } returns persisted

        val result = repository.getSettings()

        assertEquals(persisted.allocatedStorageBytes, result.allocatedStorageBytes)
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
        val entity = NodeSettingsEntity(id = 0, allocatedStorageBytes = 4L * 1024 * 1024 * 1024)
        every { dao.observeSettings() } returns flowOf(entity)

        val result = repository.observeSettings().first()

        assertEquals(entity.allocatedStorageBytes, result.allocatedStorageBytes)
    }
}
