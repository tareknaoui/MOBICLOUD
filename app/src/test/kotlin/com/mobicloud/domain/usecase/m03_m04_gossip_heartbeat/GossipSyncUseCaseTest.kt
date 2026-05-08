package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DhtEntryDto
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.ResolveDhtConflictUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GossipSyncUseCaseTest {

    private lateinit var dhtRepository: DhtRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var gossipOutboundPort: GossipOutboundPort
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var resolveDhtConflictUseCase: ResolveDhtConflictUseCase
    private lateinit var useCase: GossipSyncUseCase

    private fun peer(id: String, ip: String = "192.168.1.$id", port: Int = 9090) = Peer(
        identity = NodeIdentity(id, ByteArray(0), 1.0f),
        lastSeenTimestampMs = System.currentTimeMillis(),
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = ip,
        port = port,
        isActive = true
    )

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0

        dhtRepository = mockk()
        peerRepository = mockk()
        gossipOutboundPort = mockk()
        networkEventRepository = mockk(relaxed = true)
        securityRepository = mockk()
        resolveDhtConflictUseCase = mockk(relaxed = true)
        coEvery { securityRepository.getIdentity() } returns
            Result.success(NodeIdentity("local-node", ByteArray(0), 1.0f))
        coEvery { resolveDhtConflictUseCase.resolve(any()) } returns Result.success(Unit)
        useCase = GossipSyncUseCase(
            dhtRepository,
            peerRepository,
            gossipOutboundPort,
            networkEventRepository,
            securityRepository,
            resolveDhtConflictUseCase,
            CoroutineScope(Dispatchers.Default)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    // Test 5 : guard N=0 — aucun pair → Result.success sans envoi
    @Test
    fun `runGossipCycle with 0 active peers returns success without sending`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        coEvery { dhtRepository.observeAllEntries() } returns flowOf(emptyList())

        val result = useCase.runGossipCycle()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { gossipOutboundPort.sendBloomGossip(any(), any(), any()) }
    }

    // Test 6 : fan-out = 2 — exactement 2 pairs sélectionnés parmi 3
    @Test
    fun `runGossipCycle with 3 peers sends bloom to exactly 2 peers`() = runTest {
        val peers = listOf(peer("1"), peer("2"), peer("3"))
        every { peerRepository.peers } returns MutableStateFlow(peers)
        coEvery { dhtRepository.observeAllEntries() } returns flowOf(emptyList())
        coEvery { gossipOutboundPort.sendBloomGossip(any(), any(), any()) } returns Result.success(Unit)

        useCase.runGossipCycle()

        coVerify(exactly = 2) { gossipOutboundPort.sendBloomGossip(any(), any(), any()) }
    }

    // Test 7 : Bloom distant vide → toutes les entrées locales sont dans potentiallyMissing
    @Test
    fun `handleIncomingBloom with empty remote bloom triggers DELTA_SYNC for all local entries`() = runTest {
        val localEntries = listOf(
            DhtEntry("block-A", "node-1", "10.0.0.1", 9090, System.currentTimeMillis()),
            DhtEntry("block-B", "node-1", "10.0.0.1", 9090, System.currentTimeMillis())
        )
        // F6 fix: le pair distant doit être dans peerRepository pour que son port serveur soit résolu
        val remotePeer = peer("remote-node", ip = "10.0.0.2", port = 9090)
        every { peerRepository.peers } returns MutableStateFlow(listOf(remotePeer))
        coEvery { dhtRepository.observeAllEntries() } returns flowOf(localEntries)

        val emptyBloom = BloomFilter()
        val msg = BloomFilterGossip(
            senderNodeId = "remote-node",
            bloomFilterBytes = emptyBloom.toByteArray(),
            bloomFilterSize = emptyBloom.bitArraySize,
            numHashFunctions = emptyBloom.numHashFunctions,
            partitionIds = emptyList(),
            timestamp = System.currentTimeMillis()
        )
        coEvery { gossipOutboundPort.sendDeltaSyncRequest(any(), any(), any()) } returns Result.failure(Exception("no response needed"))

        useCase.handleIncomingBloom(msg, "10.0.0.2", 9090)

        coVerify(exactly = 1) {
            gossipOutboundPort.sendDeltaSyncRequest(
                "10.0.0.2",
                9090,
                match { it.missingBlockIds.containsAll(listOf("block-A", "block-B")) }
            )
        }
    }

    // Test 8 : Bloom distant contient toutes les entrées → aucun DELTA_SYNC envoyé
    @Test
    fun `handleIncomingBloom with full remote bloom sends no DELTA_SYNC`() = runTest {
        val localEntries = listOf(
            DhtEntry("block-X", "node-1", "10.0.0.1", 9090, System.currentTimeMillis())
        )
        every { peerRepository.peers } returns MutableStateFlow(emptyList())
        coEvery { dhtRepository.observeAllEntries() } returns flowOf(localEntries)

        val fullBloom = BloomFilter()
        fullBloom.add("block-X")
        val msg = BloomFilterGossip(
            senderNodeId = "remote-node",
            bloomFilterBytes = fullBloom.toByteArray(),
            bloomFilterSize = fullBloom.bitArraySize,
            numHashFunctions = fullBloom.numHashFunctions,
            partitionIds = listOf("block-X"),
            timestamp = System.currentTimeMillis()
        )

        useCase.handleIncomingBloom(msg, "10.0.0.2", 9090)

        coVerify(exactly = 0) { gossipOutboundPort.sendDeltaSyncRequest(any(), any(), any()) }
    }

    // Test 9 : handleDeltaRequest → DeltaSyncResponse contient les entrées demandées
    @Test
    fun `handleDeltaRequest returns DeltaSyncResponse with requested entries`() = runTest {
        val entry = DhtEntry("block-Z", "node-1", "10.0.0.1", 9090, 1000L)
        coEvery { dhtRepository.findByBlockId("block-Z") } returns Result.success(entry)
        coEvery { dhtRepository.findByBlockId("block-MISSING") } returns Result.success(null)
        // Le filtre "known peer" exige que le requester soit dans peerRepository.
        every { peerRepository.peers } returns MutableStateFlow(listOf(peer("remote-node")))

        val req = DeltaSyncRequest(
            requesterNodeId = "remote-node",
            missingBlockIds = listOf("block-Z", "block-MISSING"),
            timestamp = System.currentTimeMillis()
        )

        val result = useCase.handleDeltaRequest(req)

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals(1, response.entries.size)
        assertEquals("block-Z", response.entries.first().blockId)
    }

    // Test 10 : runGossipCycle → Result.failure si sendBloomGossip échoue (le cycle continue sans crash)
    @Test
    fun `runGossipCycle returns failure when sendBloomGossip fails`() = runTest {
        val peers = listOf(peer("1"), peer("2"))
        every { peerRepository.peers } returns MutableStateFlow(peers)
        coEvery { dhtRepository.observeAllEntries() } returns flowOf(emptyList())
        val sendError = RuntimeException("TCP timeout")
        coEvery { gossipOutboundPort.sendBloomGossip(any(), any(), any()) } returns Result.failure(sendError)

        val result = useCase.runGossipCycle()

        assertTrue(result.isFailure)
        assertEquals(sendError, result.exceptionOrNull())
    }
}
