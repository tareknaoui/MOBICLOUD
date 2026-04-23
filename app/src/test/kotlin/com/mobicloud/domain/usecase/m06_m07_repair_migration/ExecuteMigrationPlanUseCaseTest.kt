package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.HostedBlockPayload
import com.mobicloud.domain.models.MigrateBlockDirective
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExecuteMigrationPlanUseCaseTest {

    private lateinit var hostedBlockRepository: HostedBlockRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var blockSender: BlockSender
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: ExecuteMigrationPlanUseCase

    private lateinit var peersFlow: MutableStateFlow<List<Peer>>

    private val superPeerIdentity = NodeIdentity(
        nodeId = "spid".repeat(16),
        publicKeyBytes = byteArrayOf(0x10)
    )
    private val localIdentity = NodeIdentity(
        nodeId = "self".repeat(16),
        publicKeyBytes = byteArrayOf(0x20)
    )
    private val destAIdentity = NodeIdentity(
        nodeId = "dstA".repeat(16),
        publicKeyBytes = byteArrayOf(0x30)
    )

    @Before
    fun setup() {
        hostedBlockRepository = mockk()
        peerRepository = mockk()
        securityRepository = mockk()
        blockSender = mockk()
        networkEventRepository = mockk()

        peersFlow = MutableStateFlow(
            listOf(
                Peer(
                    identity = superPeerIdentity,
                    lastSeenTimestampMs = 0L,
                    source = DiscoverySource.REMOTE_FIREBASE,
                    ipAddress = "10.0.0.100",
                    port = 7000,
                    isActive = true,
                    isSuperPair = true
                )
            )
        )
        every { peerRepository.peers } returns peersFlow
        every { networkEventRepository.pushEvent(any()) } just Runs

        useCase = ExecuteMigrationPlanUseCase(
            hostedBlockRepository, peerRepository, securityRepository,
            blockSender, networkEventRepository
        )
    }

    private fun directive(
        blockId: String = "a".repeat(64),
        destinationNodeId: String = destAIdentity.nodeId,
        ip: String = "10.0.0.1",
        port: Int = 6001
    ) = MigrateBlockDirective(
        blockId = blockId,
        destinationNodeId = destinationNodeId,
        destinationIp = ip,
        destinationPort = port,
        destinationPublicKeyBytes = destAIdentity.publicKeyBytes
    )

    private fun plan(directives: List<MigrateBlockDirective> = listOf(directive())) =
        MigrationPlanMessage(
            superPeerNodeId = superPeerIdentity.nodeId,
            directives = directives,
            signatureBytes = byteArrayOf(0xAA.toByte())
        )

    private fun payload(blockId: String = "a".repeat(64)) = HostedBlockPayload(
        blockId = blockId,
        fragmentIndex = 3,
        isParity = false,
        ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        iv = ByteArray(12) { it.toByte() }
    )

    private fun ack(blockId: String) = BlockAckMessage(
        blockId = blockId,
        blockHash = "deadbeef",
        receiverNodeId = destAIdentity.nodeId,
        signature = byteArrayOf(0xBB.toByte())
    )

    @Test
    fun `test 1 - signature plan invalide - aucun sendBlock`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)

        useCase.onMigrationPlanReceived(plan())

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
    }

    @Test
    fun `test 2 - bloc absent localement - aucun sendBlock`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(null)

        useCase.onMigrationPlanReceived(plan())

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("absent localement") }) }
    }

    @Test
    fun `test 3 - transfert aveugle opaque ciphertext et iv inchanges`() = runTest {
        val original = payload()
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(original)
        val msgSlot = slot<BlockTransferMessage>()
        coEvery { blockSender.sendBlock(capture(msgSlot), any(), any()) } returns Result.success(ack(original.blockId))

        useCase.onMigrationPlanReceived(plan())

        val sent = msgSlot.captured
        assertArrayEquals(original.ciphertext, sent.ciphertext)
        assertArrayEquals(original.iv, sent.iv)
        assertEquals(original.fragmentIndex, sent.fragmentIndex)
        assertEquals(original.isParity, sent.isParity)
    }

    @Test
    fun `test 4 - execution parallele - 3 directives 2s chacune finissent en moins de 4s`() = runTest {
        // Destinations distinctes pour que le test détecte un bug "flatten on first directive" :
        // sans variation, 3 envois vers la même destination passeraient `coVerify(exactly = 3)` à tort.
        val directives = listOf(
            directive(blockId = "a".repeat(64), destinationNodeId = "d1".repeat(32), ip = "10.0.0.1", port = 6001),
            directive(blockId = "b".repeat(64), destinationNodeId = "d2".repeat(32), ip = "10.0.0.2", port = 6002),
            directive(blockId = "c".repeat(64), destinationNodeId = "d3".repeat(32), ip = "10.0.0.3", port = 6003)
        )
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { hostedBlockRepository.getBlock(any()) } answers {
            Result.success(payload(firstArg()))
        }
        coEvery { blockSender.sendBlock(any(), any(), any()) } coAnswers {
            delay(2_000L)
            Result.success(ack(arg<BlockTransferMessage>(0).blockId))
        }

        val startTime = currentTime
        useCase.onMigrationPlanReceived(plan(directives))
        val elapsed = currentTime - startTime

        assertTrue("Parallèle doit finir en < 4s (séquentiel = 6s), elapsed=$elapsed", elapsed < 4_000L)
        coVerify(exactly = 3) { blockSender.sendBlock(any(), any(), any()) }
        // Chaque directive doit atteindre SA destination — détecte un bug "flatten sur la première directive".
        coVerify(exactly = 1) { blockSender.sendBlock(any(), match { it.identity.nodeId == "d1".repeat(32) }, any()) }
        coVerify(exactly = 1) { blockSender.sendBlock(any(), match { it.identity.nodeId == "d2".repeat(32) }, any()) }
        coVerify(exactly = 1) { blockSender.sendBlock(any(), match { it.identity.nodeId == "d3".repeat(32) }, any()) }
    }

    @Test
    fun `test 5 - ownerId du BlockTransferMessage reste le noeud partant local`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(payload())
        val msgSlot = slot<BlockTransferMessage>()
        val peerSlot = slot<Peer>()
        coEvery { blockSender.sendBlock(capture(msgSlot), capture(peerSlot), any()) } returns Result.success(
            ack("a".repeat(64))
        )

        useCase.onMigrationPlanReceived(plan())

        assertEquals(
            "ownerId doit rester le nœud partant local (propriétaire d'origine)",
            localIdentity.nodeId,
            msgSlot.captured.ownerId
        )
        // La destination du Peer doit correspondre à la directive
        assertEquals(destAIdentity.nodeId, peerSlot.captured.identity.nodeId)
    }
}
