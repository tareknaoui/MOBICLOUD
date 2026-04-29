package com.mobicloud.data.p2p

import com.mobicloud.data.p2p.tcp.BlockTransferClient
import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.TransferChannelState
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.RelayRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BlockSenderWithRelayTest {

    private lateinit var tcpSender: BlockTransferClient
    private lateinit var relayRepository: RelayRepository
    private lateinit var blockSenderWithRelay: BlockSenderWithRelay

    // blockId doit être 64 chars hex lowercase (regex "^[0-9a-f]{64}$")
    private val fakeBlockId = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
    private val fakeNodeId = "abc123def456ab78"

    private val fakeBlock = BlockTransferMessage(
        blockId = fakeBlockId,
        ownerId = "owner01",
        fragmentIndex = 0,
        isParity = false,
        ciphertext = byteArrayOf(1, 2, 3, 4, 5),
        iv = ByteArray(12) { it.toByte() },
        originalFileSize = 1024L
    )

    private val fakePeer = Peer(
        identity = NodeIdentity(
            nodeId = fakeNodeId,
            publicKeyBytes = ByteArray(32),
            reliabilityScore = 1.0f
        ),
        lastSeenTimestampMs = System.currentTimeMillis(),
        source = DiscoverySource.REMOTE_FIREBASE
    )

    private val fakeTcpAck = BlockAckMessage(
        blockId = fakeBlockId,
        blockHash = fakeBlockId,
        receiverNodeId = fakeNodeId,
        signature = ByteArray(64) { 0x01 }
    )

    @Before
    fun setUp() {
        tcpSender = mockk()
        relayRepository = mockk()
        blockSenderWithRelay = BlockSenderWithRelay(tcpSender, relayRepository)
    }

    // Test 1 : TCP réussit → état DIRECT, résultat = TCP ACK, relay non appelé
    @Test
    fun `TCP reussit - etat DIRECT - relay non appele`() = runTest {
        coEvery { tcpSender.sendBlock(any(), any(), any()) } returns Result.success(fakeTcpAck)

        val result = blockSenderWithRelay.sendBlock(fakeBlock, fakePeer, 5000L)

        assertTrue(result.isSuccess)
        assertEquals(fakeTcpAck, result.getOrThrow())
        assertEquals(TransferChannelState.DIRECT, blockSenderWithRelay.transferChannelState.value)
        coVerify(exactly = 0) { relayRepository.uploadBlock(any(), any(), any()) }
    }

    // Test 2 : TCP échoue IOException → relay réussit → état RELAY_HA, ACK synthétique
    @Test
    fun `TCP echoue IOException - relay reussit - etat RELAY_HA - ACK synthetique`() = runTest {
        coEvery { tcpSender.sendBlock(any(), any(), any()) } returns Result.failure(IOException("NAT blocked"))
        coEvery { relayRepository.uploadBlock(any(), any(), any()) } returns Result.success(Unit)

        val result = blockSenderWithRelay.sendBlock(fakeBlock, fakePeer, 5000L)

        assertTrue(result.isSuccess)
        assertEquals(TransferChannelState.RELAY_HA, blockSenderWithRelay.transferChannelState.value)
        val ack = result.getOrThrow()
        assertEquals(fakeBlock.blockId, ack.blockId)
        assertEquals(fakeBlock.blockId, ack.blockHash)
        assertEquals(fakePeer.identity.nodeId, ack.receiverNodeId)
        assertEquals(0, ack.signature.size)
    }

    // Test 3 : TCP échoue + relay échoue → état OFFLINE, Result.failure
    @Test
    fun `TCP echoue + relay echoue - etat OFFLINE - result failure`() = runTest {
        coEvery { tcpSender.sendBlock(any(), any(), any()) } returns Result.failure(IOException("NAT"))
        coEvery { relayRepository.uploadBlock(any(), any(), any()) } returns Result.failure(IOException("Relay HS"))

        val result = blockSenderWithRelay.sendBlock(fakeBlock, fakePeer, 5000L)

        assertTrue(result.isFailure)
        assertEquals(TransferChannelState.OFFLINE, blockSenderWithRelay.transferChannelState.value)
    }

    // Test 4 : TCP échoue SecurityException (NACK signature) → relay NON essayé
    @Test
    fun `TCP echoue SecurityException - relay non tente - etat OFFLINE`() = runTest {
        coEvery { tcpSender.sendBlock(any(), any(), any()) } returns Result.failure(SecurityException("Signature ACK invalide"))

        val result = blockSenderWithRelay.sendBlock(fakeBlock, fakePeer, 5000L)

        assertTrue(result.isFailure)
        assertEquals(TransferChannelState.OFFLINE, blockSenderWithRelay.transferChannelState.value)
        coVerify(exactly = 0) { relayRepository.uploadBlock(any(), any(), any()) }
    }

    // Test 5 : Transparence totale — appel via interface BlockSender, même comportement
    @Test
    fun `substitution transparente via interface BlockSender`() = runTest {
        coEvery { tcpSender.sendBlock(any(), any(), any()) } returns Result.success(fakeTcpAck)

        val senderViaInterface: BlockSender = blockSenderWithRelay
        val result = senderViaInterface.sendBlock(fakeBlock, fakePeer, 5000L)

        assertTrue(result.isSuccess)
        assertEquals(fakeTcpAck, result.getOrThrow())
        assertEquals(TransferChannelState.DIRECT, blockSenderWithRelay.transferChannelState.value)
    }
}
