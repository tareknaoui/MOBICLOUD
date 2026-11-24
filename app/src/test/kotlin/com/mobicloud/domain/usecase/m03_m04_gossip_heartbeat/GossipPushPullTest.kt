package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse
import com.mobicloud.domain.models.gossip.DhtEntryDto
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.TombstoneRepository
import com.mobicloud.domain.usecase.m05_dht_catalog.ResolveDhtConflictUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Protocole gossip v1 (push-pull) : tests de bout en bout entre deux nœuds en mémoire,
 * avec la vraie résolution CRDT LWW. Répond à R1-2, R2-4 et R2-6.
 */
class GossipPushPullTest {

    /** DHT en mémoire (clé primaire = blockId, comme la table Room). */
    private class FakeDht : DhtRepository {
        val state = MutableStateFlow<Map<String, DhtEntry>>(emptyMap())
        fun put(vararg entries: DhtEntry) {
            state.value = state.value + entries.associateBy { it.blockId }
        }
        override suspend fun insertEntry(blockId: String, nodeId: String, ipAddress: String, port: Int) =
            insertEntryWithTimestamp(blockId, nodeId, ipAddress, port, System.currentTimeMillis())
        override suspend fun insertEntryWithTimestamp(
            blockId: String, nodeId: String, ipAddress: String, port: Int, timestamp: Long
        ): Result<Unit> {
            put(DhtEntry(blockId, nodeId, ipAddress, port, timestamp))
            return Result.success(Unit)
        }
        override suspend fun findByBlockId(blockId: String) = Result.success(state.value[blockId])
        override suspend fun findHostNodeIdsByBlockId(blockId: String) =
            Result.success(listOfNotNull(state.value[blockId]?.nodeId))
        override suspend fun findByNodeId(nodeId: String) =
            Result.success(state.value.values.filter { it.nodeId == nodeId })
        override suspend fun deleteByNodeId(nodeId: String): Result<Unit> {
            state.value = state.value.filterValues { it.nodeId != nodeId }
            return Result.success(Unit)
        }
        override fun observeAllEntries(): Flow<List<DhtEntry>> = state.map { it.values.toList() }
        override suspend fun remoteLookup(blockId: String, peerIp: String, peerPort: Int) =
            Result.success<DhtEntry?>(null)
    }

    /** Port sortant relié directement au nœud distant, avec compteurs de messages. */
    private class WiredPort : GossipOutboundPort {
        var remote: GossipSyncUseCase? = null
        var blooms = 0
        val deltaRequests = mutableListOf<DeltaSyncRequest>()
        override suspend fun sendBloomGossip(targetNodeId: String, msg: BloomFilterGossip): Result<Unit> {
            blooms++
            return remote!!.handleIncomingBloom(msg)
        }
        override suspend fun sendDeltaSyncRequest(targetNodeId: String, req: DeltaSyncRequest): Result<DeltaSyncResponse> {
            deltaRequests += req
            return remote!!.handleDeltaRequest(req)
        }
    }

    private class Node(val id: String, val dht: FakeDht, val port: WiredPort, val gossip: GossipSyncUseCase)

    private fun peer(id: String) = Peer(
        identity = NodeIdentity(id, ByteArray(0), 1.0f),
        lastSeenTimestampMs = System.currentTimeMillis(),
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = "10.0.0.1",
        port = 9090,
        isActive = true
    )

    private fun node(id: String, otherId: String): Node {
        val dht = FakeDht()
        val port = WiredPort()
        val peerRepository = mockk<PeerRepository>()
        every { peerRepository.peers } returns MutableStateFlow(listOf(peer(otherId)))
        val security = mockk<SecurityRepository>()
        coEvery { security.getIdentity() } returns Result.success(NodeIdentity(id, ByteArray(0), 1.0f))
        val tombstones = mockk<TombstoneRepository>()
        coEvery { tombstones.existsForBlock(any()) } returns false
        val gossip = GossipSyncUseCase(
            dht,
            peerRepository,
            port,
            mockk<NetworkEventRepository>(relaxed = true),
            security,
            ResolveDhtConflictUseCase(dht, tombstones),
            CoroutineScope(Dispatchers.Default)
        )
        return Node(id, dht, port, gossip)
    }

    private fun pair(): Pair<Node, Node> {
        val a = node("node-A", "node-B")
        val b = node("node-B", "node-A")
        a.port.remote = b.gossip
        b.port.remote = a.gossip
        return a to b
    }

    private fun entry(block: String, owner: String, ts: Long) = DhtEntry(block, owner, "10.0.0.9", 9090, ts)

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `one round converges two disjoint catalogs in both directions`() = runTest {
        val (a, b) = pair()
        a.dht.put(entry("a1", "node-A", 10), entry("a2", "node-A", 11))
        b.dht.put(entry("b1", "node-B", 12))

        assertTrue(a.gossip.runGossipCycle().isSuccess)

        val expected = setOf("a1", "a2", "b1")
        assertEquals(expected, a.dht.state.value.keys)
        assertEquals(expected, b.dht.state.value.keys)
        assertEquals(1, a.port.blooms)
        assertEquals(1, b.port.deltaRequests.size)
    }

    @Test
    fun `newer version of an entry propagates in both directions`() = runTest {
        val (a, b) = pair()
        a.dht.put(entry("x", "host-1", 5), entry("y", "host-2", 100))
        b.dht.put(entry("x", "host-3", 50), entry("y", "host-4", 1))

        a.gossip.runGossipCycle()

        assertEquals(50L, a.dht.state.value.getValue("x").timestamp)
        assertEquals("host-3", a.dht.state.value.getValue("x").nodeId)
        assertEquals(100L, b.dht.state.value.getValue("y").timestamp)
        assertEquals("host-2", b.dht.state.value.getValue("y").nodeId)
    }

    @Test
    fun `in-sync catalogs exchange no delta`() = runTest {
        val (a, b) = pair()
        val shared = arrayOf(entry("s1", "h", 1), entry("s2", "h", 2))
        a.dht.put(*shared)
        b.dht.put(*shared)

        a.gossip.runGossipCycle()

        assertEquals(1, a.port.blooms)
        assertTrue("aucun DELTA_REQ attendu", b.port.deltaRequests.isEmpty())
    }

    @Test
    fun `bloom message is v1 salted and no longer carries the full id list`() = runTest {
        val (a, _) = pair()
        a.dht.put(entry("a1", "node-A", 1))
        var captured: BloomFilterGossip? = null
        val spyPort = object : GossipOutboundPort {
            override suspend fun sendBloomGossip(targetNodeId: String, msg: BloomFilterGossip): Result<Unit> {
                captured = msg
                return Result.success(Unit)
            }
            override suspend fun sendDeltaSyncRequest(targetNodeId: String, req: DeltaSyncRequest) =
                Result.failure<DeltaSyncResponse>(UnsupportedOperationException())
        }
        val peerRepository = mockk<PeerRepository>()
        every { peerRepository.peers } returns MutableStateFlow(listOf(peer("node-B")))
        val security = mockk<SecurityRepository>()
        coEvery { security.getIdentity() } returns Result.success(NodeIdentity("node-A", ByteArray(0), 1.0f))
        val sender = GossipSyncUseCase(
            a.dht, peerRepository, spyPort, mockk(relaxed = true), security,
            mockk(relaxed = true), CoroutineScope(Dispatchers.Default)
        )

        sender.runGossipCycle()

        val msg = assertNotNullAndGet(captured)
        assertEquals(GossipSyncUseCase.PROTOCOL_VERSION, msg.protocolVersion)
        assertNotEquals(0L, msg.salt)
        assertTrue(msg.partitionIds.isEmpty())
    }

    @Test
    fun `every tenth incoming round uses exact anti-entropy keys`() = runTest {
        val (a, b) = pair()
        a.dht.put(entry("a1", "node-A", 1))
        b.dht.put(entry("b1", "node-B", 2), entry("b2", "node-B", 3))

        // Rounds successifs initiés par A : B reçoit le Bloom et émet le DELTA_REQ
        repeat(GossipSyncUseCase.ANTI_ENTROPY_EVERY.toInt()) {
            a.dht.put(entry("a-new-$it", "node-A", 100L + it)) // force un écart à chaque round
            a.gossip.runGossipCycle()
        }

        val requests = b.port.deltaRequests
        val antiEntropy = requests.filter { it.antiEntropy }
        assertEquals(1, antiEntropy.size)
        val ae = antiEntropy.single()
        assertNull(ae.requesterDigest)
        val bKeysAtThatTime = ae.requesterKnownKeys.toSet()
        assertTrue(bKeysAtThatTime.any { it.startsWith("b1|") })
        assertTrue(requests.filterNot { it.antiEntropy }.all { it.requesterDigest != null })
        // Convergence finale
        assertEquals(a.dht.state.value.keys, b.dht.state.value.keys)
    }

    @Test
    fun `anti-entropy request returns exactly what the requester lacks`() = runTest {
        val (a, _) = pair()
        a.dht.put(entry("k1", "h", 1), entry("k2", "h", 2), entry("k3", "h", 3))

        val resp = a.gossip.handleDeltaRequest(
            DeltaSyncRequest(
                requesterNodeId = "node-B",
                missingBlockIds = emptyList(),
                timestamp = 0L,
                requesterKnownKeys = listOf(GossipSyncUseCase.versionKey(entry("k2", "h", 2))),
                antiEntropy = true
            )
        ).getOrThrow()

        assertEquals(setOf("k1", "k3"), resp.entries.map { it.blockId }.toSet())
    }

    @Test
    fun `pushed entries above the cap are rejected`() = runTest {
        val (a, _) = pair()
        val tooMany = (0..GossipSyncUseCase.MAX_PUSHED_ENTRIES).map {
            DhtEntryDto("p$it", "h", "10.0.0.1", 9090, 1L)
        }
        val result = a.gossip.handleDeltaRequest(
            DeltaSyncRequest(
                requesterNodeId = "node-B",
                missingBlockIds = emptyList(),
                timestamp = 0L,
                pushedEntries = tooMany,
                antiEntropy = true
            )
        )
        assertTrue(result.isFailure)
        assertTrue(a.dht.state.value.isEmpty())
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
