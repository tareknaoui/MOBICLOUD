package com.mobicloud.data.p2p.tcp

import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.domain.models.BlockAckMessage
import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.SecurityRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket

/**
 * Tests adversariaux du binding ACK <-> contexte d'envoi dans [BlockTransferClient].
 *
 * Sans ces verifications, un peer malveillant peut signer un ACK avec un
 * receiverNodeId/blockHash arbitraires : la signature reste valide pour SA cle,
 * mais l'attestation ne couvre pas vraiment "j'ai stocke le bloc que tu m'as
 * envoye". Le DHT/catalog enregistrerait alors des placements fantomes.
 *
 * On lance un faux serveur TCP qui repond avec un ACK forge (signature mockee
 * acceptee), et on verifie que BlockTransferClient rejette quand mismatch.
 */
class BlockTransferClientAckBindingTest {

    private val expectedBlockId = "a".repeat(64) // sha256 hex 64 chars
    private val attackerNodeId = "ATTACKER--peerXX" // 16 chars
    private val victimNodeId = "VICTIM----peerYY"

    private val peerIdentity = NodeIdentity(
        nodeId = attackerNodeId,
        publicKeyBytes = ByteArray(65)
    )

    private lateinit var serverSocket: ServerSocket
    private var serverPort: Int = 0

    @Before
    fun setUp() {
        serverSocket = ServerSocket(0)
        serverPort = serverSocket.localPort
    }

    @After
    fun tearDown() {
        runCatching { serverSocket.close() }
    }

    private fun peer() = Peer(
        identity = peerIdentity,
        lastSeenTimestampMs = System.currentTimeMillis(),
        isActive = true,
        isSuperPair = false,
        ipAddress = "127.0.0.1",
        port = serverPort
    )

    private fun block() = BlockTransferMessage(
        blockId = expectedBlockId,
        ownerId = "owner-XX",
        fragmentIndex = 0,
        isParity = false,
        ciphertext = ByteArray(64),
        iv = ByteArray(12),
        originalFileSize = 1024L
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun startServerThatRepliesWith(ack: BlockAckMessage) = Thread {
        val client = serverSocket.accept()
        try {
            val inp = DataInputStream(client.getInputStream())
            inp.readByte() // discriminator BLOCK_TRANSFER
            val len = inp.readInt()
            val buf = ByteArray(len)
            inp.readFully(buf) // ignore content

            val out = DataOutputStream(client.getOutputStream())
            val ackBytes = MobiCloudProtoBuf.encodeToByteArray(BlockAckMessage.serializer(), ack)
            out.writeByte(BlockTransferChannel.BLOCK_ACK.toInt())
            out.writeInt(ackBytes.size)
            out.write(ackBytes)
            out.flush()
        } catch (_: Exception) {
            // socket fermee tot par le client = OK
        } finally {
            runCatching { client.close() }
        }
    }.also { it.start() }

    private fun securityMockAcceptingSignatures(): SecurityRepository = mockk<SecurityRepository>().also {
        // On veut isoler les checks de binding (receiverNodeId/blockHash). La
        // signature est consideree valide pour focaliser le test.
        coEvery { it.verifySignature(any(), any(), any()) } returns Result.success(true)
    }

    // ── A1 — receiverNodeId different du peer cible -> rejet ─────────────────

    @Test
    fun `A1 - ACK avec receiverNodeId different du peer cible est rejete`() = runTest {
        val forgedAck = BlockAckMessage(
            blockId = expectedBlockId,
            blockHash = expectedBlockId,
            receiverNodeId = victimNodeId, // FORGE : pas le peer auquel on a envoye
            signature = ByteArray(64)
        )
        startServerThatRepliesWith(forgedAck)

        val client = BlockTransferClient(securityMockAcceptingSignatures())
        val result = withContext(Dispatchers.IO) {
            client.sendBlock(block(), peer(), timeoutMs = 2_000L)
        }

        assertTrue("ACK avec receiverNodeId forge doit etre rejete", result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Erreur doit mentionner receiverNodeId mismatch (msg=$msg)",
            msg.contains("receiverNodeId mismatch", ignoreCase = true)
        )
    }

    // ── A2 — blockHash different du blockId envoye -> rejet ──────────────────

    @Test
    fun `A2 - ACK avec blockHash different du bloc envoye est rejete`() = runTest {
        val differentHash = "b".repeat(64)
        val forgedAck = BlockAckMessage(
            blockId = expectedBlockId,
            blockHash = differentHash, // FORGE : ACK pour un autre bloc
            receiverNodeId = attackerNodeId,
            signature = ByteArray(64)
        )
        startServerThatRepliesWith(forgedAck)

        val client = BlockTransferClient(securityMockAcceptingSignatures())
        val result = withContext(Dispatchers.IO) {
            client.sendBlock(block(), peer(), timeoutMs = 2_000L)
        }

        assertTrue("ACK avec blockHash forge doit etre rejete", result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Erreur doit mentionner blockHash mismatch (msg=$msg)",
            msg.contains("blockHash mismatch", ignoreCase = true)
        )
    }

    // ── A3 — ACK conforme passe (controle positif) ───────────────────────────

    @Test
    fun `A3 - ACK avec receiverNodeId et blockHash corrects est accepte`() = runTest {
        val validAck = BlockAckMessage(
            blockId = expectedBlockId,
            blockHash = expectedBlockId,        // == blockId envoye
            receiverNodeId = attackerNodeId,    // == peer cible
            signature = ByteArray(64)
        )
        startServerThatRepliesWith(validAck)

        val client = BlockTransferClient(securityMockAcceptingSignatures())
        val result = withContext(Dispatchers.IO) {
            client.sendBlock(block(), peer(), timeoutMs = 2_000L)
        }

        assertTrue(
            "ACK conforme doit passer (result=${result.exceptionOrNull()?.message})",
            result.isSuccess
        )
    }
}
