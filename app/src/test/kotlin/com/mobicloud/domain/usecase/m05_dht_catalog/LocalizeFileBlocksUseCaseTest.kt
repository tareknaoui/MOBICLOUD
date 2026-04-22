package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.PeerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalizeFileBlocksUseCaseTest {

    private lateinit var catalogRepository: CatalogRepository
    private lateinit var dhtRepository: DhtRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var useCase: LocalizeFileBlocksUseCase

    @Before
    fun setUp() {
        catalogRepository = mockk()
        dhtRepository = mockk()
        peerRepository = mockk()
        useCase = LocalizeFileBlocksUseCase(catalogRepository, dhtRepository, peerRepository)
    }

    // --- Builder helpers ---

    private fun buildPeer(
        nodeId: String,
        score: Float,
        ip: String = "192.168.1.1",
        port: Int = 9000,
        active: Boolean = true
    ) = Peer(
        identity = NodeIdentity(nodeId, ByteArray(0), score),
        lastSeenTimestampMs = 0L,
        ipAddress = ip,
        port = port,
        isActive = active
    )

    private fun buildCatalogEntry(fileHash: String, fragments: List<FragmentLocation>) =
        CatalogEntry(fileHash, "ownerHash", System.currentTimeMillis(), fragments)

    private fun buildFragmentLocation(index: Int, fragmentHash: String, nodeIds: List<String>) =
        FragmentLocation(index, fragmentHash, nodeIds)

    private fun buildDhtEntry(blockId: String, nodeId: String, ip: String = "10.0.0.1", port: Int = 8000) =
        DhtEntry(blockId, nodeId, ip, port, System.currentTimeMillis())

    // --- Tests ---

    @Test
    fun `primary path - active peer matching nodeIds returns ResolvedBlockLocation`() = runTest(UnconfinedTestDispatcher()) {
        val peer = buildPeer("node-A", score = 0.9f, ip = "192.168.1.10", port = 9001)
        val fragment = buildFragmentLocation(0, "block-hash-001", listOf("node-A"))
        val entry = buildCatalogEntry("file-hash-001", listOf(fragment))

        coEvery { catalogRepository.getEntry("file-hash-001") } returns Result.success(entry)
        coEvery { peerRepository.peers } returns MutableStateFlow(listOf(peer))
        coEvery { dhtRepository.findByBlockId(any()) } returns Result.success(null)
        coEvery { dhtRepository.remoteLookup(any(), any(), any()) } returns Result.success(null)

        val result = useCase.invoke("file-hash-001")

        assertTrue(result.isSuccess)
        val map = result.getOrThrow()
        assertEquals(1, map.size)
        val resolved = map["block-hash-001"]!!
        assertEquals("node-A", resolved.nodeId)
        assertEquals("192.168.1.10", resolved.ipAddress)
        assertEquals(9001, resolved.port)
        assertEquals(0.9f, resolved.reliabilityScore)
    }

    @Test
    fun `tie-breaking - two active peers for same block, higher reliabilityScore wins`() = runTest(UnconfinedTestDispatcher()) {
        val peerLow = buildPeer("node-B", score = 0.5f, ip = "192.168.1.20", port = 9002)
        val peerHigh = buildPeer("node-C", score = 0.95f, ip = "192.168.1.30", port = 9003)
        val fragment = buildFragmentLocation(0, "block-tie-001", listOf("node-B", "node-C"))
        val entry = buildCatalogEntry("file-tie-001", listOf(fragment))

        coEvery { catalogRepository.getEntry("file-tie-001") } returns Result.success(entry)
        coEvery { peerRepository.peers } returns MutableStateFlow(listOf(peerLow, peerHigh))
        coEvery { dhtRepository.findByBlockId(any()) } returns Result.success(null)
        coEvery { dhtRepository.remoteLookup(any(), any(), any()) } returns Result.success(null)

        val result = useCase.invoke("file-tie-001")

        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()["block-tie-001"]!!
        assertEquals("node-C", resolved.nodeId)
        assertEquals(0.95f, resolved.reliabilityScore)
    }

    @Test
    fun `dht fallback - no active peer matches nodeIds, falls back to dhtRepository`() = runTest(UnconfinedTestDispatcher()) {
        val peer = buildPeer("node-unrelated", score = 0.8f, ip = "10.0.0.5", port = 9005)
        val fragment = buildFragmentLocation(0, "block-dht-001", listOf("node-absent"))
        val entry = buildCatalogEntry("file-dht-001", listOf(fragment))
        val dhtEntry = buildDhtEntry("block-dht-001", "node-absent", "10.0.0.10", 8010)

        coEvery { catalogRepository.getEntry("file-dht-001") } returns Result.success(entry)
        coEvery { peerRepository.peers } returns MutableStateFlow(listOf(peer))
        coEvery { dhtRepository.findByBlockId("block-dht-001") } returns Result.success(dhtEntry)
        coEvery { dhtRepository.remoteLookup(any(), any(), any()) } returns Result.success(null)

        val result = useCase.invoke("file-dht-001")

        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()["block-dht-001"]!!
        assertEquals("node-absent", resolved.nodeId)
        assertEquals("10.0.0.10", resolved.ipAddress)
        assertEquals(8010, resolved.port)
    }

    @Test
    fun `file not found - catalogRepository returns null yields failure with FileNotFoundException`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { catalogRepository.getEntry("unknown-hash") } returns Result.success(null)
        coEvery { peerRepository.peers } returns MutableStateFlow(emptyList())

        val result = useCase.invoke("unknown-hash")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is FileNotFoundException)
        assertTrue(exception!!.message!!.contains("unknown-hash"))
    }

    @Test
    fun `partial result - 1 of 6 blocks unresolvable returns map with 5 entries`() = runTest(UnconfinedTestDispatcher()) {
        val peer = buildPeer("node-D", score = 0.7f, ip = "192.168.2.1", port = 9010)
        // 5 fragments resolved via PRIMARY, 1 unresolvable
        val fragments = (0 until 5).map { i ->
            buildFragmentLocation(i, "block-ok-00$i", listOf("node-D"))
        } + listOf(buildFragmentLocation(5, "block-orphan-005", listOf("node-missing")))
        val entry = buildCatalogEntry("file-partial-001", fragments)

        coEvery { catalogRepository.getEntry("file-partial-001") } returns Result.success(entry)
        coEvery { peerRepository.peers } returns MutableStateFlow(listOf(peer))
        coEvery { dhtRepository.findByBlockId("block-orphan-005") } returns Result.success(null)
        coEvery { dhtRepository.remoteLookup("block-orphan-005", any(), any()) } returns Result.success(null)
        // Primary blocks don't need DHT
        for (i in 0 until 5) {
            coEvery { dhtRepository.findByBlockId("block-ok-00$i") } returns Result.success(null)
        }

        val result = useCase.invoke("file-partial-001")

        assertTrue(result.isSuccess)
        val map = result.getOrThrow()
        assertEquals(5, map.size)
        for (i in 0 until 5) {
            assertNotNull(map["block-ok-00$i"])
        }
        assertTrue(map["block-orphan-005"] == null)
    }
}
