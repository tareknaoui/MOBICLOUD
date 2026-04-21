package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.WrappedFileMasterKey
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.InsertDhtEntryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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

    private lateinit var useCase: DistributeEncryptedBlocksUseCase

    private val localNodeId = "node_local_01"
    private val localPublicKey = Random.nextBytes(65)
    private val localIdentity = NodeIdentity(localNodeId, localPublicKey)

    @Before
    fun setUp() {
        peerRepository = mockk()
        blockSender = mockk()
        catalogRepository = mockk()
        gossipSyncUseCase = mockk()
        securityRepository = mockk()
        insertDhtEntryUseCase = mockk()

        useCase = DistributeEncryptedBlocksUseCase(
            peerRepository = peerRepository,
            blockSender = blockSender,
            catalogRepository = catalogRepository,
            gossipSyncUseCase = gossipSyncUseCase,
            securityRepository = securityRepository,
            insertDhtEntryUseCase = insertDhtEntryUseCase
        )

        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { catalogRepository.insertOwnerEntry(any()) } returns Result.success(Unit)
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)
        coEvery { insertDhtEntryUseCase(any(), any(), any(), any()) } returns Result.success(Unit)
    }

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

        val result = useCase.distribute(bundle, "filehash_abc", k = 4)

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

        val result = useCase.distribute(bundle, "filehash_retry", k = 4)

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

        val result = useCase.distribute(bundle, "filehash_fail", k = 4)

        assertTrue("distribute should fail when < k data blocks confirmed", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)

        coVerify(exactly = 0) { catalogRepository.insertOwnerEntry(any()) }
    }

    /**
     * Test 4 — Aucun pair actif → Result.failure immédiat.
     */
    @Test
    fun `distribute failure - no active peers`() = runTest {
        every { peerRepository.peers } returns MutableStateFlow(emptyList())

        val bundle = fakeBundle(k = 4, n = 2)

        val result = useCase.distribute(bundle, "filehash_nopeer", k = 4)

        assertTrue("distribute should fail with no active peers", result.isFailure)
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

        val result = useCase.distribute(bundle, "filehash_key", k = 4)

        assertTrue(result.isSuccess)
        val entry = capturedEntry
        assertTrue("CatalogEntry doit contenir wrappedMasterKey", entry?.wrappedMasterKey != null)
        assertTrue(
            "wrappedMasterKey doit correspondre à celui du bundle",
            entry?.wrappedMasterKey == bundle.wrappedFileMasterKey
        )
    }
}
