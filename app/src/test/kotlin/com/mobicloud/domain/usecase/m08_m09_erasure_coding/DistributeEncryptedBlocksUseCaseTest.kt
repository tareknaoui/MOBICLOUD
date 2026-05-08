package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.models.WrappedFileMasterKey
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.InsertDhtEntryUseCase
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import kotlin.random.Random

class DistributeEncryptedBlocksUseCaseTest {

    private lateinit var peerRepository: PeerRepository
    private lateinit var blockSender: BlockSender
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var gossipSyncUseCase: GossipSyncUseCase
    private lateinit var securityRepository: SecurityRepository
    private lateinit var insertDhtEntryUseCase: InsertDhtEntryUseCase
    private lateinit var requestInterClusterHostingUseCase: RequestInterClusterHostingUseCase
    private lateinit var nodeSettingsRepository: NodeSettingsRepository

    private lateinit var useCase: DistributeEncryptedBlocksUseCase

    private val localNodeId = "node_local_01"
    private val localPublicKey = Random.nextBytes(65)
    private val localIdentity = NodeIdentity(localNodeId, localPublicKey)
    private val localClusterId = "cluster-local-AAAA"

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        peerRepository = mockk()
        blockSender = mockk()
        catalogRepository = mockk()
        gossipSyncUseCase = mockk()
        securityRepository = mockk()
        insertDhtEntryUseCase = mockk()
        requestInterClusterHostingUseCase = mockk()
        nodeSettingsRepository = mockk()

        useCase = DistributeEncryptedBlocksUseCase(
            peerRepository = peerRepository,
            blockSender = blockSender,
            catalogRepository = catalogRepository,
            gossipSyncUseCase = gossipSyncUseCase,
            securityRepository = securityRepository,
            insertDhtEntryUseCase = insertDhtEntryUseCase,
            requestInterClusterHostingUseCase = requestInterClusterHostingUseCase,
            nodeSettingsRepository = nodeSettingsRepository
        )

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { catalogRepository.insertOwnerEntry(any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)
        coEvery { insertDhtEntryUseCase(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { nodeSettingsRepository.getSettings() } returns NodeSettings(
            allocatedStorageBytes = 1_000_000_000L,
            clusterId = localClusterId
        )
        // Default — pas de candidat inter-cluster sauf override par test
        every { requestInterClusterHostingUseCase.selectRemoteHost(any(), any()) } returns null
        every {
            requestInterClusterHostingUseCase.selectRemoteHosts(any(), any(), any())
        } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun fakeRemoteRelayPeer(
        nodeId: String = "remote-super-pair",
        ip: String = "10.99.0.1",
        port: Int = 9999,
        clusterId: String = "cluster-remote-XXXX",
        freeBytes: Long = 100_000_000L
    ): RelayPeer = RelayPeer(
        nodeId = nodeId,
        ip = ip,
        port = port,
        reliabilityScore = 0.9f,
        lastSeen = System.currentTimeMillis(),
        isSuperPair = true,
        clusterId = clusterId,
        freeBytes = freeBytes
    )

    // --- Helpers ---

    private fun fakeEncryptedFragment(index: Int, isParity: Boolean = false): EncryptedFragment =
        EncryptedFragment(
            index = index,
            isParity = isParity,
            ciphertext = Random.nextBytes(100),
            iv = Random.nextBytes(12),
            originalFileSize = 1024L
        )

    private fun fakePeer(nodeId: String, suffix: Int = 1): Peer =
        Peer(
            identity = NodeIdentity(nodeId, Random.nextBytes(65)),
            lastSeenTimestampMs = System.currentTimeMillis(),
            source = DiscoverySource.REMOTE_FIREBASE,
            ipAddress = "192.168.1.$suffix",
            port = 9000 + suffix,
            isActive = true
        )

    private fun fakeBundle(k: Int = 4, n: Int = 2): EncryptedBundle {
        val fragments = (0 until k).map { fakeEncryptedFragment(it) } +
                (k until k + n).map { fakeEncryptedFragment(it, isParity = true) }
        return EncryptedBundle(
            encryptedFragments = fragments,
            wrappedFileMasterKey = WrappedFileMasterKey(
                ephemeralPublicKeyBytes = Random.nextBytes(65),
                iv = Random.nextBytes(12),
                encryptedKey = Random.nextBytes(48)
            )
        )
    }

    private fun fakeAck(blockId: String, receiverNodeId: String): BlockAckMessage =
        BlockAckMessage(
            blockId = blockId,
            blockHash = blockId,
            receiverNodeId = receiverNodeId,
            signature = ByteArray(0)
        )

    // --- Tests ---

    /**
     * Test 1 — Happy path: K+N=6 fragments, 6 peers, toutes les livraisons réussissent.
     * Vérifie que insertOwnerEntry et runGossipCycle sont appelés 1 fois chacun,
     * et que insertDhtEntryUseCase est appelé 6 fois.
     */
    @Test
    fun `distribute success - all blocks confirmed`() = runTest {
        val peers = (1..6).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 4, n = 2)
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(fakeAck(msg.blockId, "node_receiver"))
        }

        val result = useCase.distribute(bundle, "filehash_abc", ErasureParameters(k = 4, n = 2), selectedPeers = peers)

        assertTrue("distribute should succeed", result.isSuccess)
        val entry = result.getOrThrow()
        assertEquals(6, entry.fragmentLocations.size)

        coVerify(exactly = 1) { catalogRepository.insertOwnerEntry(any()) }
        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
        coVerify(exactly = 6) { insertDhtEntryUseCase(any(), any(), any(), any()) }
    }

    /**
     * Test 2 — Timeout sur pair primaire + retry réussi sur pair de remplacement.
     * Résultat global doit être succès.
     */
    @Test
    fun `distribute success - timeout on primary then fallback succeeds`() = runTest {
        val peers = (1..6).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 4, n = 2)

        var callCount = 0
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            callCount++
            if (callCount == 1) {
                // Premier appel (fragment 0, pair primaire) → timeout
                Result.failure(SocketTimeoutException("timeout"))
            } else {
                val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
                Result.success(fakeAck(msg.blockId, "node_receiver"))
            }
        }

        val result = useCase.distribute(bundle, "filehash_retry", ErasureParameters(k = 4, n = 2), selectedPeers = peers)

        assertTrue("distribute should succeed after retry", result.isSuccess)
    }

    /**
     * Test 3 — Moins de K confirmations de données → Result.failure.
     * insertOwnerEntry ne doit PAS être appelé.
     */
    @Test
    fun `distribute failure - less than k data blocks confirmed`() = runTest {
        val peers = (1..6).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 4, n = 2)

        // Tous les fragments de données (index 0..3) échouent, parité (4..5) réussit
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            if (!msg.isParity) {
                Result.failure(SocketTimeoutException("data block timeout"))
            } else {
                Result.success(fakeAck(msg.blockId, "node_receiver"))
            }
        }

        val result = useCase.distribute(bundle, "filehash_fail", ErasureParameters(k = 4, n = 2), selectedPeers = peers)

        assertTrue("distribute should fail when < k data blocks confirmed", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)

        coVerify(exactly = 0) { catalogRepository.insertOwnerEntry(any()) }
    }

    /**
     * Test 4 — selectedPeers vide → aucun sendBlock local, fallback inter-cluster attendu.
     * distribute() lui-même ne fail pas : c'est la job de SelectOptimalPeersUseCase de lever
     * l'erreur avant. Ici on teste le comportement de distribute() avec liste vide.
     */
    @Test
    fun `distribute failure - no active peers`() = runTest {
        val bundle = fakeBundle(k = 4, n = 2)

        val result = useCase.distribute(bundle, "filehash_nopeer", ErasureParameters(k = 4, n = 2), selectedPeers = emptyList())

        assertTrue("distribute should fail when selectedPeers empty and no inter-cluster", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify(exactly = 0) { catalogRepository.insertOwnerEntry(any()) }
    }

    /**
     * Test 5 — Vérification CatalogEntry contient wrappedMasterKey.
     */
    @Test
    fun `distribute success - catalogEntry contains wrappedMasterKey`() = runTest {
        val peers = (1..6).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 4, n = 2)
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(fakeAck(msg.blockId, "node_receiver"))
        }

        var capturedEntry: CatalogEntry? = null
        coEvery { catalogRepository.insertOwnerEntry(any()) } answers {
            capturedEntry = firstArg()
            Result.success(Unit)
        }

        val result = useCase.distribute(bundle, "filehash_key", ErasureParameters(k = 4, n = 2), selectedPeers = peers)

        assertTrue(result.isSuccess)
        val entry = capturedEntry
        assertTrue("CatalogEntry doit contenir wrappedMasterKey", entry?.wrappedMasterKey != null)
        assertTrue(
            "wrappedMasterKey doit correspondre à celui du bundle",
            entry?.wrappedMasterKey == bundle.wrappedFileMasterKey
        )
    }

    // -------------------------------------------------------------------------
    // Story 9.3 — Fallback inter-cluster
    // -------------------------------------------------------------------------

    /**
     * Test k/n dynamiques — profil 4G (K=3, N=3) persisté dans CatalogEntry.
     * Garantit la symétrie encode/decode : le décodage utilisera le bon K.
     */
    @Test
    fun `distribute persiste k et n du profil 4G dans CatalogEntry`() = runTest {
        val peers = (1..6).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 3, n = 3)
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(fakeAck(msg.blockId, "node_receiver"))
        }

        var capturedEntry: CatalogEntry? = null
        coEvery { catalogRepository.insertOwnerEntry(any()) } answers {
            capturedEntry = firstArg()
            Result.success(Unit)
        }

        val result = useCase.distribute(bundle, "filehash_4g", ErasureParameters(k = 3, n = 3), selectedPeers = peers)

        assertTrue(result.isSuccess)
        assertEquals("CatalogEntry.k doit valoir 3 (profil 4G)", 3, capturedEntry?.k)
        assertEquals("CatalogEntry.n doit valoir 3 (profil 4G)", 3, capturedEntry?.n)
    }

    /**
     * Test k/n dynamiques — profil fallback (K=2, N=4) persisté dans CatalogEntry.
     */
    @Test
    fun `distribute persiste k et n du profil fallback dans CatalogEntry`() = runTest {
        val peers = (1..6).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 2, n = 4)
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(fakeAck(msg.blockId, "node_receiver"))
        }

        var capturedEntry: CatalogEntry? = null
        coEvery { catalogRepository.insertOwnerEntry(any()) } answers {
            capturedEntry = firstArg()
            Result.success(Unit)
        }

        val result = useCase.distribute(bundle, "filehash_fallback", ErasureParameters(k = 2, n = 4), selectedPeers = peers)

        assertTrue(result.isSuccess)
        assertEquals("CatalogEntry.k doit valoir 2 (profil fallback)", 2, capturedEntry?.k)
        assertEquals("CatalogEntry.n doit valoir 4 (profil fallback)", 4, capturedEntry?.n)
    }

    // -------------------------------------------------------------------------
    // Story 9.3 — Fallback inter-cluster
    // -------------------------------------------------------------------------

    /**
     * Story 9.3 Cas A (AC#3, AC#5) — cluster local vide ⇒ fallback inter-cluster invoqué ;
     * sendBlock est appelé sur le pair distant, et insertDhtEntryUseCase reçoit
     * remoteNodeId/remoteIp/remotePort.
     */
    @Test
    fun `Story 9_3 fallback inter-cluster invoque quand cluster local est vide`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(emptyList())

        val remote = fakeRemoteRelayPeer(nodeId = "remote-A", ip = "10.99.0.7", port = 7777)
        every { requestInterClusterHostingUseCase.selectRemoteHosts(any(), any(), any()) } returns listOf(remote)

        val bundle = fakeBundle(k = 4, n = 2)
        val capturedPeers = mutableListOf<Peer>()
        coEvery { blockSender.sendBlock(any(), capture(capturedPeers), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(fakeAck(msg.blockId, "remote-A"))
        }

        val ipSlot = slot<String>()
        val portSlot = slot<Int>()
        val nodeIdSlot = slot<String>()
        coEvery {
            insertDhtEntryUseCase(any(), capture(nodeIdSlot), capture(ipSlot), capture(portSlot))
        } returns Result.success(Unit)

        val result = useCase.distribute(bundle, "filehash_intercluster", ErasureParameters(k = 4, n = 2), selectedPeers = emptyList())

        assertTrue("distribute should succeed via inter-cluster", result.isSuccess)
        // Tous les fragments routés vers le pair distant
        assertTrue("tous les sendBlock doivent cibler le pair distant", capturedPeers.all { it.ipAddress == "10.99.0.7" && it.port == 7777 })
        verify(atLeast = 1) { requestInterClusterHostingUseCase.selectRemoteHosts(any(), any(), any()) }
        // insertDhtEntryUseCase reçoit l'IP/port distants
        assertEquals("10.99.0.7", ipSlot.captured)
        assertEquals(7777, portSlot.captured)
        assertEquals("remote-A", nodeIdSlot.captured)
    }

    /**
     * Story 9.3 Cas B (AC#4) — placement local primary réussit ⇒ selectRemoteHost
     * JAMAIS appelé (régression-safe : aucun appel inter-cluster sur path heureux local).
     */
    @Test
    fun `Story 9_3 selectRemoteHost jamais appele quand placement local reussit`() = runTest {
        val peers = (1..6).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 4, n = 2)
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
            Result.success(fakeAck(msg.blockId, "node_receiver"))
        }

        val result = useCase.distribute(bundle, "filehash_local_only", ErasureParameters(k = 4, n = 2), selectedPeers = peers)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { requestInterClusterHostingUseCase.selectRemoteHosts(any(), any(), any()) }
    }

    /**
     * Story 9.3 Cas C (AC#4) — placement local échoue ET selectRemoteHost retourne null
     * ⇒ Result.failure (data blocks confirmés < k). Comportement actuel préservé.
     */
    @Test
    fun `Story 9_3 echec total quand local echoue et inter-cluster indisponible`() = runTest {
        val peers = (1..2).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val bundle = fakeBundle(k = 4, n = 2)
        // Tous les envois échouent (local primary + local fallback)
        coEvery { blockSender.sendBlock(any(), any(), any()) } returns
            Result.failure(SocketTimeoutException("all peers down"))
        every { requestInterClusterHostingUseCase.selectRemoteHosts(any(), any(), any()) } returns emptyList()

        val result = useCase.distribute(bundle, "filehash_total_fail", ErasureParameters(k = 4, n = 2), selectedPeers = peers)

        assertTrue("distribute doit échouer si local et inter-cluster indisponibles", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { catalogRepository.insertOwnerEntry(any()) }
    }

    /**
     * Story 9.3 (AC#3) — placement local échoue (primary + fallback) puis inter-cluster
     * réussit ⇒ succès global, fragment placé sur le pair distant.
     */
    @Test
    fun `Story 9_3 fallback inter-cluster apres echec local primary et fallback`() = runTest {
        val peers = (1..2).map { fakePeer("node_$it", it) }
        every { peerRepository.peers } returns MutableStateFlow(peers)

        val remote = fakeRemoteRelayPeer(nodeId = "remote-B", ip = "10.99.0.8", port = 8888)
        every { requestInterClusterHostingUseCase.selectRemoteHosts(any(), any(), any()) } returns listOf(remote)

        val bundle = fakeBundle(k = 4, n = 2)
        coEvery { blockSender.sendBlock(any(), any(), any()) } answers {
            val targetPeer = secondArg<Peer>()
            if (targetPeer.ipAddress == "10.99.0.8") {
                val msg = firstArg<com.mobicloud.domain.models.BlockTransferMessage>()
                Result.success(fakeAck(msg.blockId, "remote-B"))
            } else {
                Result.failure(SocketTimeoutException("local peer down"))
            }
        }

        val result = useCase.distribute(bundle, "filehash_local_to_remote", ErasureParameters(k = 4, n = 2), selectedPeers = peers)

        assertTrue(result.isSuccess)
        verify(atLeast = 1) { requestInterClusterHostingUseCase.selectRemoteHosts(any(), any(), any()) }
    }
}
