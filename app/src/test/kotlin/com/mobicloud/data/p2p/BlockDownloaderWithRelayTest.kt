package com.mobicloud.data.p2p

import android.util.Log
import com.mobicloud.data.p2p.tcp.BlockDownloadClient
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.RelayRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest

/**
 * Story 9.4 — tests du wrapper BlockDownloaderWithRelay.
 *
 * Validation : routage direct vs relay-pull selon présence du nodeId dans peerRepository.peers,
 * et application stricte des invariants sha256 / fragmentIndex / iv.size sur la branche relay.
 */
class BlockDownloaderWithRelayTest {

    private lateinit var direct: BlockDownloadClient
    private lateinit var relay: RelayRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var peers: MutableStateFlow<List<Peer>>

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        direct = mockk(relaxed = true)
        relay = mockk(relaxed = true)
        peerRepository = mockk(relaxed = true)
        peers = MutableStateFlow(emptyList())
        every { peerRepository.peers } returns peers
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b)
            .joinToString("") { "%02x".format(it) }

    private fun makePeer(nodeId: String, isActive: Boolean = true, ip: String? = "10.0.0.1", port: Int? = 9000): Peer =
        Peer(
            identity = NodeIdentity(nodeId, ByteArray(0)),
            lastSeenTimestampMs = 1_000L,
            source = DiscoverySource.LAN_MULTICAST,
            ipAddress = ip,
            port = port,
            isActive = isActive,
            isSuperPair = false
        )

    private fun makeMsg(
        ciphertext: ByteArray = ByteArray(32) { it.toByte() },
        fragmentIndex: Int = 0,
        iv: ByteArray = ByteArray(12),
        blockIdOverride: String? = null
    ): BlockTransferMessage {
        val blockId = blockIdOverride ?: sha256Hex(ciphertext)
        return BlockTransferMessage(
            blockId = blockId,
            ownerId = "owner",
            fragmentIndex = fragmentIndex,
            isParity = false,
            ciphertext = ciphertext,
            iv = iv,
            originalFileSize = 0L
        )
    }

    private fun makeLocation(blockId: String, fragmentIndex: Int = 0, nodeId: String = "remote0000000001"): ResolvedBlockLocation =
        ResolvedBlockLocation(
            blockId = blockId,
            fragmentIndex = fragmentIndex,
            nodeId = nodeId,
            ipAddress = "10.0.0.99",
            port = 9100,
            reliabilityScore = 0f
        )

    // ── Routage : intra-cluster → direct ────────────────────────────────────────

    @Test
    fun `intra-cluster — peer actif present delegue a direct`() = runBlocking {
        val nodeId = "intra00000000001"
        peers.value = listOf(makePeer(nodeId))
        val location = makeLocation("a".repeat(64), 0, nodeId)

        coEvery { direct.downloadBlock(location, 1_000L) } returns Result.success(
            DownloadedBlock("a".repeat(64), 0, false, ByteArray(32), ByteArray(12), 5L)
        )

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { direct.downloadBlock(location, 1_000L) }
        coVerify(exactly = 0) { relay.requestBlock(any(), any(), any()) }
    }

    // ── Routage : inter-cluster → relay ─────────────────────────────────────────

    @Test
    fun `inter-cluster — peer absent delegue a relay`() = runBlocking {
        val nodeId = "remote00000000001"
        peers.value = emptyList()  // pas de pair connu localement
        val ciphertext = ByteArray(64) { (it * 7).toByte() }
        val blockId = sha256Hex(ciphertext)
        val location = makeLocation(blockId, 5, nodeId)

        val msg = makeMsg(ciphertext = ciphertext, fragmentIndex = 5, iv = ByteArray(12) { it.toByte() })
        coEvery { relay.requestBlock(nodeId, blockId, 2_000L) } returns Result.success(msg)

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 2_000L)

        assertTrue(result.isSuccess)
        val block = result.getOrNull()!!
        assertEquals(blockId, block.blockId)
        assertEquals(5, block.fragmentIndex)
        assertArrayEquals(ciphertext, block.ciphertext)
        coVerify(exactly = 0) { direct.downloadBlock(any(), any()) }
        coVerify(exactly = 1) { relay.requestBlock(nodeId, blockId, 2_000L) }
    }

    // ── Pair présent mais inactif → relay ───────────────────────────────────────

    @Test
    fun `peer present mais inactif bascule sur relay-pull`() = runBlocking {
        val nodeId = "stale0000000001"
        peers.value = listOf(makePeer(nodeId, isActive = false))
        val ciphertext = ByteArray(16)
        val blockId = sha256Hex(ciphertext)
        val location = makeLocation(blockId, 0, nodeId)

        coEvery { relay.requestBlock(nodeId, blockId, any()) } returns Result.success(makeMsg(ciphertext))

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { direct.downloadBlock(any(), any()) }
        coVerify(exactly = 1) { relay.requestBlock(nodeId, blockId, any()) }
    }

    // ── Pair présent mais ipAddress null → relay ────────────────────────────────

    @Test
    fun `peer present mais ipAddress null bascule sur relay-pull`() = runBlocking {
        val nodeId = "noip000000000001"
        peers.value = listOf(makePeer(nodeId, ip = null))
        val ciphertext = ByteArray(16)
        val blockId = sha256Hex(ciphertext)
        val location = makeLocation(blockId, 0, nodeId)

        coEvery { relay.requestBlock(nodeId, blockId, any()) } returns Result.success(makeMsg(ciphertext))

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { direct.downloadBlock(any(), any()) }
        coVerify(exactly = 1) { relay.requestBlock(nodeId, blockId, any()) }
    }

    // ── Validation : hash mismatch ──────────────────────────────────────────────

    @Test
    fun `relay branch — hash mismatch retourne SecurityException`() = runBlocking {
        val nodeId = "remote00000000001"
        peers.value = emptyList()
        val ciphertext = ByteArray(32) { (it + 1).toByte() }
        val correctBlockId = sha256Hex(ciphertext)
        // Le serveur distant retourne un msg avec un blockId qui ne correspond pas au ciphertext
        val msg = makeMsg(
            ciphertext = ByteArray(32) { 0xAA.toByte() }, // différent
            blockIdOverride = correctBlockId
        )
        val location = makeLocation(correctBlockId, 0, nodeId)
        coEvery { relay.requestBlock(any(), any(), any()) } returns Result.success(msg)

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Hash mismatch"))
    }

    // ── Validation : fragment mismatch ──────────────────────────────────────────

    @Test
    fun `relay branch — fragmentIndex mismatch retourne SecurityException`() = runBlocking {
        val nodeId = "remote00000000001"
        peers.value = emptyList()
        val ciphertext = ByteArray(32)
        val blockId = sha256Hex(ciphertext)
        // Distant retourne fragmentIndex=7 alors qu'on attendait 3
        val msg = makeMsg(ciphertext = ciphertext, fragmentIndex = 7)
        val location = makeLocation(blockId, fragmentIndex = 3, nodeId)
        coEvery { relay.requestBlock(any(), any(), any()) } returns Result.success(msg)

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Fragment mismatch"))
    }

    // ── Validation : iv invalide ────────────────────────────────────────────────

    @Test
    fun `relay branch — iv size invalide retourne IOException`() = runBlocking {
        val nodeId = "remote00000000001"
        peers.value = emptyList()
        val ciphertext = ByteArray(32)
        val blockId = sha256Hex(ciphertext)
        val msg = makeMsg(ciphertext = ciphertext, iv = ByteArray(11))  // pas 12
        val location = makeLocation(blockId, 0, nodeId)
        coEvery { relay.requestBlock(any(), any(), any()) } returns Result.success(msg)

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("IV size invalide"))
    }

    // ── Relay timeout propagé ───────────────────────────────────────────────────

    @Test
    fun `relay branch — timeout propage en Result failure`() = runBlocking {
        val nodeId = "remote00000000001"
        peers.value = emptyList()
        val location = makeLocation("a".repeat(64), 0, nodeId)
        coEvery { relay.requestBlock(any(), any(), any()) } returns Result.failure(
            SocketTimeoutException("simulé")
        )

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SocketTimeoutException)
    }

    // ── latencyMs : direct préservé / relay calculé wall-clock (spec ligne 680) ─

    @Test
    fun `intra-cluster — latencyMs du BlockDownloadClient est conserve tel quel`() = runBlocking {
        val nodeId = "intra000latency1"
        peers.value = listOf(makePeer(nodeId))
        val blockId = "b".repeat(64)
        val location = makeLocation(blockId, 0, nodeId)

        val expectedLatencyMs = 42L
        coEvery { direct.downloadBlock(location, 1_000L) } returns Result.success(
            DownloadedBlock(blockId, 0, false, ByteArray(32), ByteArray(12), expectedLatencyMs)
        )

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isSuccess)
        assertEquals(
            "latencyMs direct doit être préservé tel quel",
            expectedLatencyMs,
            result.getOrNull()!!.latencyMs
        )
    }

    @Test
    fun `inter-cluster — latencyMs est calcule wall-clock localement`() = runBlocking {
        val nodeId = "remote0latency01"
        peers.value = emptyList()
        val ciphertext = ByteArray(32) { (it + 3).toByte() }
        val blockId = sha256Hex(ciphertext)
        val location = makeLocation(blockId, 0, nodeId)

        coEvery { relay.requestBlock(nodeId, blockId, any()) } coAnswers {
            kotlinx.coroutines.delay(50)  // simule un round-trip réseau
            Result.success(makeMsg(ciphertext = ciphertext))
        }

        val downloader = BlockDownloaderWithRelay(direct, relay, peerRepository)
        val result = downloader.downloadBlock(location, 1_000L)

        assertTrue(result.isSuccess)
        val latency = result.getOrNull()!!.latencyMs
        assertTrue(
            "latencyMs branche relay doit être positif (wall-clock), got=$latency",
            latency >= 50L
        )
    }
}
