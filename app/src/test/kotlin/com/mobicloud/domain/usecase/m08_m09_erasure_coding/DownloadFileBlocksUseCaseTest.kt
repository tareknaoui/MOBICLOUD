package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.BlockDownloader
import com.mobicloud.domain.repository.PeerRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * Story 6.2 — Task 15. Tests JVM purs ; aucun ServerSocket réel n'est utilisé : `BlockDownloader`
 * est mocké via mockk. La race K+2 est testée via `delay()` dans `coAnswers`.
 */
class DownloadFileBlocksUseCaseTest {

    private lateinit var blockDownloader: BlockDownloader
    private lateinit var peerRepository: PeerRepository
    private lateinit var useCase: DownloadFileBlocksUseCase

    @Before
    fun setUp() {
        blockDownloader = mockk()
        peerRepository = mockk()
        // Default : aucun pair de fallback (chaque test peut surcharger).
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        useCase = DownloadFileBlocksUseCase(blockDownloader, peerRepository)
    }

    // --- Helpers ---

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun blockIdFor(fragmentIndex: Int): String =
        sha256Hex(ByteArray(32) { fragmentIndex.toByte() })

    private fun location(
        fragmentIndex: Int,
        nodeId: String = "node-$fragmentIndex",
        score: Float = 1.0f
    ) = ResolvedBlockLocation(
        blockId = blockIdFor(fragmentIndex),
        fragmentIndex = fragmentIndex,
        nodeId = nodeId,
        ipAddress = "10.0.0.${fragmentIndex + 1}",
        port = 9000 + fragmentIndex,
        reliabilityScore = score
    )

    private fun downloadedBlock(loc: ResolvedBlockLocation) = DownloadedBlock(
        blockId = loc.blockId,
        fragmentIndex = loc.fragmentIndex,
        isParity = false,
        ciphertext = ByteArray(32) { loc.fragmentIndex.toByte() }
    )

    private fun fakePeer(nodeId: String, suffix: Int) = Peer(
        identity = NodeIdentity(nodeId, ByteArray(0), 1.0f),
        lastSeenTimestampMs = 0L,
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = "192.168.0.$suffix",
        port = 7000 + suffix,
        isActive = true
    )

    // --- Test 1 : Happy path K+2, les K premiers gagnent ---
    @Test
    fun `invoke completes when k fast blocks arrive before slow ones`() = runTest {
        val k = 4
        val locations = (0 until k + 2).map { location(it) }
        val blockMap = locations.associateBy { it.blockId }

        coEvery { blockDownloader.downloadBlock(any(), any()) } coAnswers {
            val loc = firstArg<ResolvedBlockLocation>()
            // 4 premiers rapides, 2 derniers très lents
            if (loc.fragmentIndex < k) delay(10) else delay(10_000)
            Result.success(downloadedBlock(loc))
        }

        val emissions = useCase.invoke(blockMap, k).toList()

        val terminal = emissions.last()
        assertTrue("expected Completed, got $terminal", terminal is DownloadProgressState.Completed)
        terminal as DownloadProgressState.Completed
        assertEquals(k, terminal.blocks.size)
        // Les 4 premiers fragmentIndex sont présents
        assertEquals((0 until k).toSet(), terminal.blocks.keys)
    }

    // --- Test 2 : Hash mismatch primaire → fallback réussit ---
    @Test
    fun `invoke succeeds when primary fails and fallback peer recovers the block`() = runTest {
        val k = 4
        val locations = (0 until k).map { location(it) }
        val blockMap = locations.associateBy { it.blockId }

        // Pair de fallback disponible
        every { peerRepository.peers } returns MutableStateFlow(listOf(fakePeer("node-fallback", 99)))

        coEvery { blockDownloader.downloadBlock(any(), any()) } coAnswers {
            val loc = firstArg<ResolvedBlockLocation>()
            val timeout = secondArg<Long>()
            // Le pair primaire du fragment 0 échoue (hash mismatch). Tout le reste réussit.
            if (loc.fragmentIndex == 0 && loc.nodeId == "node-0") {
                Result.failure(SecurityException("hash mismatch"))
            } else {
                // Le fallback est appelé avec MAX_ACK_TIMEOUT_MS — vérifier le routage.
                if (loc.nodeId == "node-fallback") {
                    assertEquals(DownloadFileBlocksUseCase.MAX_ACK_TIMEOUT_MS, timeout)
                }
                Result.success(downloadedBlock(loc))
            }
        }

        val emissions = useCase.invoke(blockMap, k).toList()
        val terminal = emissions.last()
        assertTrue("expected Completed, got $terminal", terminal is DownloadProgressState.Completed)
        assertEquals(k, (terminal as DownloadProgressState.Completed).blocks.size)
    }

    // --- Test 3 : Échec définitif < k blocs valides ---
    @Test
    fun `invoke fails when fewer than k blocks are recoverable`() = runTest {
        val k = 4
        val locations = (0 until k).map { location(it) }
        val blockMap = locations.associateBy { it.blockId }

        coEvery { blockDownloader.downloadBlock(any(), any()) } coAnswers {
            val loc = firstArg<ResolvedBlockLocation>()
            if (loc.fragmentIndex == 0) Result.failure(java.io.IOException("not found"))
            else Result.success(downloadedBlock(loc))
        }

        val emissions = useCase.invoke(blockMap, k).toList()
        val terminal = emissions.last()
        assertTrue("expected Failed, got $terminal", terminal is DownloadProgressState.Failed)
        terminal as DownloadProgressState.Failed
        assertEquals(3, terminal.received)
        assertEquals(k, terminal.k)
        assertTrue(terminal.reason.contains("3/4"))
    }

    // --- Test 4 : blockMap insuffisant → Failed immédiat ---
    @Test
    fun `invoke emits Failed immediately when blockMap is smaller than k`() = runTest {
        val k = 4
        val locations = (0 until 3).map { location(it) }
        val blockMap = locations.associateBy { it.blockId }

        val emissions = useCase.invoke(blockMap, k).toList()
        assertEquals(1, emissions.size)
        val only = emissions.first()
        assertTrue(only is DownloadProgressState.Failed)
        only as DownloadProgressState.Failed
        assertEquals(0, only.received)
        assertTrue(only.reason.contains("Insuffisant"))
    }

    // --- Test 5 : Streaming Progress ---
    @Test
    fun `invoke emits Progress per received block then Completed`() = runTest {
        val k = 4
        val locations = (0 until k).map { location(it) }
        val blockMap = locations.associateBy { it.blockId }

        coEvery { blockDownloader.downloadBlock(any(), any()) } coAnswers {
            Result.success(downloadedBlock(firstArg()))
        }

        val emissions = useCase.invoke(blockMap, k).toList()

        val progress = emissions.filterIsInstance<DownloadProgressState.Progress>()
        assertEquals(k, progress.size)
        // Compteur strictement croissant
        progress.forEachIndexed { idx, p ->
            assertEquals(idx + 1, p.received)
            assertEquals(k, p.k)
            assertEquals(0, p.failed)
        }
        assertTrue(emissions.last() is DownloadProgressState.Completed)
    }

    // --- Test 6 : Dédupe par fragmentIndex ---
    @Test
    fun `invoke deduplicates blocks sharing the same fragmentIndex`() = runTest {
        val k = 2
        // 4 locations dont 2 partagent fragmentIndex=0 (réplique).
        val locations = listOf(
            location(0, nodeId = "node-A"),
            location(0, nodeId = "node-B").copy(blockId = blockIdFor(0)),  // même blockId
            location(1, nodeId = "node-C"),
            location(1, nodeId = "node-D").copy(blockId = blockIdFor(1))
        )
        // blockMap : key = "blockId|nodeId" pour qu'on ait 4 entrées distinctes.
        val blockMap = locations.associateBy { "${it.blockId}|${it.nodeId}" }

        coEvery { blockDownloader.downloadBlock(any(), any()) } coAnswers {
            Result.success(downloadedBlock(firstArg()))
        }

        val emissions = useCase.invoke(blockMap, k).toList()
        val terminal = emissions.last()
        assertTrue(terminal is DownloadProgressState.Completed)
        terminal as DownloadProgressState.Completed
        // Seulement 2 fragments uniques malgré 4 réceptions potentielles.
        assertEquals(2, terminal.blocks.size)
        assertEquals(setOf(0, 1), terminal.blocks.keys)
        assertNotNull(terminal.blocks[0])
        assertNotNull(terminal.blocks[1])
    }
}
