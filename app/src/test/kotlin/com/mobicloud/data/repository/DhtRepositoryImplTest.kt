package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.DhtDao
import com.mobicloud.data.local.entity.DhtEntryEntity
import com.mobicloud.domain.models.DhtEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DhtRepositoryImplTest {

    private lateinit var dhtDao: DhtDao
    private lateinit var repository: DhtRepositoryImpl

    @Before
    fun setup() {
        dhtDao = mockk()
        repository = DhtRepositoryImpl(dhtDao)
    }

    @Test
    fun testInsertEntrySuccessfully() = runTest {
        coEvery { dhtDao.insert(any()) } returns Unit

        val result = repository.insertEntry("block-123", "node-1", "192.168.1.1", 5000)

        assertTrue(result.isSuccess)
        coVerify { dhtDao.insert(any()) }
    }

    @Test
    fun testFindByBlockIdReturnsEntry() = runTest {
        val entity = DhtEntryEntity("block-123", "node-1", "192.168.1.1", 5000, 1000L)
        coEvery { dhtDao.findByBlockId("block-123") } returns entity

        val result = repository.findByBlockId("block-123")

        assertTrue(result.isSuccess)
        val entry = result.getOrNull()
        assertEquals("block-123", entry?.blockId)
        assertEquals("node-1", entry?.nodeId)
        assertEquals("192.168.1.1", entry?.ipAddress)
        assertEquals(5000, entry?.port)
    }

    @Test
    fun testFindByBlockIdReturnsNullWhenNotFound() = runTest {
        coEvery { dhtDao.findByBlockId("unknown") } returns null

        val result = repository.findByBlockId("unknown")

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun testFindByNodeIdReturnsAllEntries() = runTest {
        val entries = listOf(
            DhtEntryEntity("block-1", "node-1", "192.168.1.1", 5000, 1000L),
            DhtEntryEntity("block-2", "node-1", "192.168.1.1", 5000, 2000L),
            DhtEntryEntity("block-3", "node-1", "192.168.1.1", 5000, 3000L)
        )
        coEvery { dhtDao.findByNodeId("node-1") } returns entries

        val result = repository.findByNodeId("node-1")

        assertTrue(result.isSuccess)
        val foundEntries = result.getOrNull()
        assertEquals(3, foundEntries?.size)
        assertEquals("block-1", foundEntries?.get(0)?.blockId)
        assertEquals("block-2", foundEntries?.get(1)?.blockId)
        assertEquals("block-3", foundEntries?.get(2)?.blockId)
    }

    @Test
    fun testDeleteByNodeIdSuccessfully() = runTest {
        coEvery { dhtDao.deleteByNodeId("node-1") } returns Unit

        val result = repository.deleteByNodeId("node-1")

        assertTrue(result.isSuccess)
        coVerify { dhtDao.deleteByNodeId("node-1") }
    }

    @Test
    fun testObserveAllEntriesReturnsFlow() = runTest {
        val entities = listOf(
            DhtEntryEntity("block-1", "node-1", "192.168.1.1", 5000, 1000L),
            DhtEntryEntity("block-2", "node-2", "192.168.1.2", 5001, 2000L)
        )
        coEvery { dhtDao.observeAllEntries() } returns flowOf(entities)

        val flow = repository.observeAllEntries()
        var collectedEntries: List<DhtEntry>? = null

        flow.collect {
            collectedEntries = it
        }

        assertEquals(2, collectedEntries?.size)
        assertEquals("block-1", collectedEntries?.get(0)?.blockId)
        assertEquals("block-2", collectedEntries?.get(1)?.blockId)
    }

    @Test
    fun testInsertEntryHandlesException() = runTest {
        coEvery { dhtDao.insert(any()) } throws RuntimeException("DB error")

        val result = repository.insertEntry("block-123", "node-1", "192.168.1.1", 5000)

        assertTrue(result.isFailure)
    }

    @Test
    fun testFindByNodeIdReturnsEmptyListWhenNotFound() = runTest {
        coEvery { dhtDao.findByNodeId("unknown") } returns emptyList()

        val result = repository.findByNodeId("unknown")

        assertTrue(result.isSuccess)
        val entries = result.getOrNull()
        assertEquals(0, entries?.size)
    }
}
