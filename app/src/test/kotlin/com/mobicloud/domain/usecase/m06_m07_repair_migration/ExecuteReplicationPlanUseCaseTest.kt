package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.HostedBlockPayload
import com.mobicloud.domain.models.MigrateBlockDirective
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.ReplicationPlanMessage
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExecuteReplicationPlanUseCaseTest {

    private lateinit var hostedBlockRepository: HostedBlockRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var securityRepository: SecurityRepository
    private lateinit var blockSender: BlockSender
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var useCase: ExecuteReplicationPlanUseCase

    private lateinit var peersFlow: MutableStateFlow<List<Peer>>

    private val superPeerIdentity = NodeIdentity(
        nodeId = "spid".repeat(16),
        publicKeyBytes = byteArrayOf(0x10)
    )
    private val localIdentity = NodeIdentity(
        nodeId = "self".repeat(16),
        publicKeyBytes = byteArrayOf(0x20)
    )
    private val destIdentity = NodeIdentity(
        nodeId = "dest".repeat(16),
        publicKeyBytes = byteArrayOf(0x30)
    )

    private fun peer(
        identity: NodeIdentity,
        isActive: Boolean = true,
        isSuperPair: Boolean = false
    ) = Peer(
        identity = identity,
        lastSeenTimestampMs = 0L,
        source = DiscoverySource.REMOTE_FIREBASE,
        ipAddress = "10.0.0.1",
        port = 7000,
        isActive = isActive,
        isSuperPair = isSuperPair
    )

    @Before
    fun setup() {
        hostedBlockRepository = mockk()
        peerRepository = mockk()
        securityRepository = mockk()
        blockSender = mockk()
        networkEventRepository = mockk()

        peersFlow = MutableStateFlow(listOf(peer(superPeerIdentity, isSuperPair = true)))
        every { peerRepository.peers } returns peersFlow
        every { networkEventRepository.pushEvent(any()) } just Runs
        // getIdentity() est appelée tôt (garde anti-self) → fournir un défaut pour tous les tests.
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)

        useCase = ExecuteReplicationPlanUseCase(
            hostedBlockRepository, peerRepository, securityRepository,
            blockSender, networkEventRepository
        )
    }

    private fun directive(
        blockId: String = "a".repeat(64),
        destinationNodeId: String = destIdentity.nodeId,
        ip: String = "10.0.0.3",
        port: Int = 6003
    ) = MigrateBlockDirective(
        blockId = blockId,
        destinationNodeId = destinationNodeId,
        destinationIp = ip,
        destinationPort = port,
        destinationPublicKeyBytes = destIdentity.publicKeyBytes
    )

    private fun plan(d: MigrateBlockDirective = directive(), superPeerNodeId: String = superPeerIdentity.nodeId) =
        ReplicationPlanMessage(
            superPeerNodeId = superPeerNodeId,
            directive = d,
            signatureBytes = byteArrayOf(0xAA.toByte())
        )

    private fun payload(blockId: String = "a".repeat(64)) = HostedBlockPayload(
        blockId = blockId,
        fragmentIndex = 2,
        isParity = false,
        ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05),
        iv = ByteArray(12) { it.toByte() }
    )

    private fun ack(blockId: String) = BlockAckMessage(
        blockId = blockId,
        blockHash = "deadbeef",
        receiverNodeId = destIdentity.nodeId,
        signature = byteArrayOf(0xBB.toByte())
    )

    @Test
    fun `test 1 - emetteur non Super-Pair - aucun sendBlock`() = runTest {
        // peers contient le super-peer mais marqué isSuperPair = false
        peersFlow.value = listOf(peer(superPeerIdentity, isSuperPair = false))

        useCase.onReplicationPlanReceived(plan())

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("non Super-Pair") }) }
    }

    @Test
    fun `test 2 - signature invalide - aucun sendBlock`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(false)

        useCase.onReplicationPlanReceived(plan())

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("Signature plan invalide") }) }
    }

    @Test
    fun `test 3 - destination invalide ip vide - aucun sendBlock`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)

        useCase.onReplicationPlanReceived(plan(d = directive(ip = "")))

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("destination invalide") }) }
    }

    @Test
    fun `test 3b - destination invalide port negatif - aucun sendBlock`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)

        useCase.onReplicationPlanReceived(plan(d = directive(port = -1)))

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("destination invalide") }) }
    }

    @Test
    fun `test 4 - bloc absent localement - aucun sendBlock`() = runTest {
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(null)

        useCase.onReplicationPlanReceived(plan())

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("absent localement") }) }
    }

    @Test
    fun `test 5 - transfert aveugle opaque - ciphertext et iv inchanges`() = runTest {
        val original = payload()
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(original)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        val msgSlot = slot<BlockTransferMessage>()
        coEvery { blockSender.sendBlock(capture(msgSlot), any(), any()) } returns
            Result.success(ack(original.blockId))

        useCase.onReplicationPlanReceived(plan())

        val sent = msgSlot.captured
        assertArrayEquals("ciphertext doit être byte-à-byte identique", original.ciphertext, sent.ciphertext)
        assertArrayEquals("iv doit être byte-à-byte identique", original.iv, sent.iv)
        assertEquals(original.fragmentIndex, sent.fragmentIndex)
        assertEquals(original.isParity, sent.isParity)
        assertEquals(original.blockId, sent.blockId)
    }

    @Test
    fun `test 6 - ACK confirme - log confirme avec receiverNodeId tronque`() = runTest {
        val original = payload()
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(original)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { blockSender.sendBlock(any(), any(), any()) } returns Result.success(ack(original.blockId))

        useCase.onReplicationPlanReceived(plan())

        coVerify { networkEventRepository.pushEvent(match { it.contains("confirmé") }) }
    }

    @Test
    fun `test 7 - sendBlock echoue - log echec propage`() = runTest {
        val original = payload()
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(original)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { blockSender.sendBlock(any(), any(), any()) } returns
            Result.failure(RuntimeException("connexion refusée"))

        useCase.onReplicationPlanReceived(plan())

        coVerify { networkEventRepository.pushEvent(match { it.contains("échec") }) }
    }

    @Test
    fun `test 8 - ownerId du BlockTransferMessage est le nodeId local du donneur`() = runTest {
        val original = payload()
        coEvery { securityRepository.verifySignature(any(), any(), any()) } returns Result.success(true)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(original)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        val msgSlot = slot<BlockTransferMessage>()
        val peerSlot = slot<Peer>()
        coEvery { blockSender.sendBlock(capture(msgSlot), capture(peerSlot), any()) } returns
            Result.success(ack(original.blockId))

        useCase.onReplicationPlanReceived(plan())

        assertEquals(localIdentity.nodeId, msgSlot.captured.ownerId)
        assertEquals(destIdentity.nodeId, peerSlot.captured.identity.nodeId)
        assertEquals("10.0.0.3", peerSlot.captured.ipAddress)
        assertEquals(6003, peerSlot.captured.port)
    }

    @Test
    fun `test 9 - signature verifiee avec payload tag REPAIR et publicKey du SP`() = runTest {
        val original = payload()
        val dataSlot = slot<ByteArray>()
        val pubKeySlot = slot<ByteArray>()
        coEvery {
            securityRepository.verifySignature(capture(dataSlot), any(), capture(pubKeySlot))
        } returns Result.success(true)
        coEvery { hostedBlockRepository.getBlock(any()) } returns Result.success(original)
        coEvery { securityRepository.getIdentity() } returns Result.success(localIdentity)
        coEvery { blockSender.sendBlock(any(), any(), any()) } returns Result.success(ack(original.blockId))

        useCase.onReplicationPlanReceived(plan())

        val payloadStr = String(dataSlot.captured)
        // Le tag REPAIR doit figurer — anti-collision avec signature MIGRATION_PLAN de Story 7.2
        assert(payloadStr.contains("|REPAIR|"))
        assertArrayEquals(superPeerIdentity.publicKeyBytes, pubKeySlot.captured)
    }

    @Test
    fun `test 10 - super-peer inconnu dans le registry - ignore`() = runTest {
        peersFlow.value = emptyList()  // pas de pair connu

        useCase.onReplicationPlanReceived(plan())

        coVerify(exactly = 0) { blockSender.sendBlock(any(), any(), any()) }
        coVerify { networkEventRepository.pushEvent(match { it.contains("non Super-Pair") }) }
    }
}
