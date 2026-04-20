package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.TombstoneEntry
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.TombstoneRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolveDhtConflictUseCaseTest {

    private lateinit var dhtRepository: DhtRepository
    private lateinit var tombstoneRepository: TombstoneRepository
    private lateinit var useCase: ResolveDhtConflictUseCase

    private fun entry(
        blockId: String = "block-1",
        nodeId: String = "node-A",
        timestamp: Long = 1000L
    ) = DhtEntry(blockId = blockId, nodeId = nodeId, ipAddress = "10.0.0.1", port = 9090, timestamp = timestamp)

    @Before
    fun setup() {
        dhtRepository = mockk()
        tombstoneRepository = mockk()
        coEvery { tombstoneRepository.existsForBlock(any()) } returns false
        coEvery { dhtRepository.insertEntryWithTimestamp(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        useCase = ResolveDhtConflictUseCase(dhtRepository, tombstoneRepository)
    }

    // Test 1 — LWW : entrée distante plus récente remplace locale
    @Test
    fun `resolve replaces local entry when remote timestamp is newer`() = runTest {
        val local = entry(timestamp = 1000L)
        val remote = entry(timestamp = 2000L)
        coEvery { dhtRepository.findByBlockId("block-1") } returns Result.success(local)

        val result = useCase.resolve(remote)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dhtRepository.insertEntryWithTimestamp("block-1", "node-A", "10.0.0.1", 9090, 2000L) }
    }

    // Test 2 — LWW : entrée locale plus récente est conservée
    @Test
    fun `resolve keeps local entry when local timestamp is newer`() = runTest {
        val local = entry(timestamp = 2000L)
        val remote = entry(timestamp = 1000L)
        coEvery { dhtRepository.findByBlockId("block-1") } returns Result.success(local)

        val result = useCase.resolve(remote)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { dhtRepository.insertEntryWithTimestamp(any(), any(), any(), any(), any()) }
    }

    // Test 3 — Tie-break : timestamps identiques, remote.nodeId > local.nodeId
    @Test
    fun `resolve uses remote entry on tie-break when remote nodeId is lexicographically greater`() = runTest {
        val local = entry(nodeId = "aaa", timestamp = 1000L)
        val remote = entry(nodeId = "zzz", timestamp = 1000L)
        coEvery { dhtRepository.findByBlockId("block-1") } returns Result.success(local)

        val result = useCase.resolve(remote)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dhtRepository.insertEntryWithTimestamp("block-1", "zzz", "10.0.0.1", 9090, 1000L) }
    }

    // Test 4 — Tie-break : timestamps identiques, local.nodeId > remote.nodeId
    @Test
    fun `resolve keeps local entry on tie-break when local nodeId is lexicographically greater`() = runTest {
        val local = entry(nodeId = "zzz", timestamp = 1000L)
        val remote = entry(nodeId = "aaa", timestamp = 1000L)
        coEvery { dhtRepository.findByBlockId("block-1") } returns Result.success(local)

        val result = useCase.resolve(remote)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { dhtRepository.insertEntryWithTimestamp(any(), any(), any(), any(), any()) }
    }

    // Test 5 — Anti-résurrection : tombstone existe pour blockId
    @Test
    fun `resolve ignores entry when tombstone exists for blockId`() = runTest {
        coEvery { tombstoneRepository.existsForBlock("block-1") } returns true

        val result = useCase.resolve(entry())

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { dhtRepository.findByBlockId(any()) }
        coVerify(exactly = 0) { dhtRepository.insertEntryWithTimestamp(any(), any(), any(), any(), any()) }
    }

    // Test 6 — Nouvelle entrée (pas de local)
    @Test
    fun `resolve inserts entry directly when no local entry exists`() = runTest {
        coEvery { dhtRepository.findByBlockId("block-1") } returns Result.success(null)

        val result = useCase.resolve(entry(timestamp = 500L))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dhtRepository.insertEntryWithTimestamp("block-1", "node-A", "10.0.0.1", 9090, 500L) }
    }

    // Test 7 — tombstone() insère un TombstoneEntry
    @Test
    fun `tombstone inserts a TombstoneEntry for the given blockId`() = runTest {
        coEvery { tombstoneRepository.insert(any()) } returns Result.success(Unit)

        val result = useCase.tombstone("block-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            tombstoneRepository.insert(match { it.blockId == "block-1" })
        }
    }

    // Test 8 — purgeExpiredTombstones() délègue à tombstoneRepository.deleteOlderThan(cutoff)
    @Test
    fun `purgeExpiredTombstones delegates to tombstoneRepository with 24h cutoff`() = runTest {
        coEvery { tombstoneRepository.deleteOlderThan(any()) } returns Result.success(3)

        val before = System.currentTimeMillis()
        val result = useCase.purgeExpiredTombstones()
        val after = System.currentTimeMillis()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            tombstoneRepository.deleteOlderThan(
                match { cutoff ->
                    val expectedMin = before - ResolveDhtConflictUseCase.TOMBSTONE_MAX_AGE_MS
                    val expectedMax = after - ResolveDhtConflictUseCase.TOMBSTONE_MAX_AGE_MS
                    cutoff in expectedMin..expectedMax
                }
            )
        }
    }
}
