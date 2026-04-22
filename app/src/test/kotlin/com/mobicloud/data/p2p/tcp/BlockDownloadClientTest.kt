package com.mobicloud.data.p2p.tcp

import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.domain.models.BlockResponseMessage
import com.mobicloud.domain.models.ResolvedBlockLocation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest

/**
 * Story 6.2 — Task 14. Tests JVM purs avec un `ServerSocket` local qui simule un serveur
 * minimal et répond avec un frame Protobuf `BLOCK_RESPONSE` (ou variations selon le test).
 */
@OptIn(ExperimentalSerializationApi::class)
class BlockDownloadClientTest {

    private lateinit var server: ServerSocket
    private lateinit var serverThread: Thread
    private lateinit var client: BlockDownloadClient
    @Volatile private var serverError: Throwable? = null

    @Before
    fun setUp() {
        server = ServerSocket(0)
        client = BlockDownloadClient()
    }

    @After
    fun tearDown() {
        try { server.close() } catch (_: Exception) {}
        try { serverThread.join(2000) } catch (_: Exception) {}
    }

    /** Lance un thread serveur qui exécute [handler] sur la première connexion entrante. */
    private fun startServer(handler: (Socket) -> Unit) {
        serverThread = Thread {
            try {
                val s = server.accept()
                s.use(handler)
            } catch (t: Throwable) {
                if (!server.isClosed) serverError = t
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun location(blockId: String, port: Int = server.localPort) = ResolvedBlockLocation(
        blockId = blockId,
        fragmentIndex = 0,
        nodeId = "node-test",
        ipAddress = "127.0.0.1",
        port = port,
        reliabilityScore = 1.0f
    )

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun readRequest(s: Socket): ByteArray {
        val inp = DataInputStream(s.getInputStream())
        val disc = inp.readByte()
        assertEquals(BlockTransferChannel.BLOCK_REQUEST, disc)
        val len = inp.readInt()
        val bytes = ByteArray(len)
        inp.readFully(bytes)
        return bytes
    }

    private fun writeResponse(s: Socket, resp: BlockResponseMessage) {
        val out = DataOutputStream(s.getOutputStream())
        val bytes = MobiCloudProtoBuf.encodeToByteArray(BlockResponseMessage.serializer(), resp)
        out.writeByte(BlockTransferChannel.BLOCK_RESPONSE.toInt())
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    // --- Test 1 : Happy path ---
    @Test
    fun `downloadBlock returns success when server responds with valid ciphertext`() = runTest {
        val ciphertext = ByteArray(64) { (it and 0xFF).toByte() }
        val blockId = sha256Hex(ciphertext)
        startServer { s ->
            readRequest(s)
            writeResponse(s, BlockResponseMessage(blockId, fragmentIndex = 7, isParity = true, ciphertext = ciphertext))
        }

        val result = client.downloadBlock(location(blockId), timeoutMs = 2_000L)

        assertTrue("expected success but got ${result.exceptionOrNull()}", result.isSuccess)
        val block = result.getOrThrow()
        assertEquals(blockId, block.blockId)
        assertEquals(7, block.fragmentIndex)
        assertTrue(block.isParity)
        assertArrayEquals(ciphertext, block.ciphertext)
        assertTrue(serverError == null)
    }

    // --- Test 2 : Hash mismatch ---
    @Test
    fun `downloadBlock fails with SecurityException when ciphertext hash mismatches`() = runTest {
        val expected = sha256Hex(ByteArray(32) { 1 })
        val tampered = ByteArray(32) { 9 }  // hash != expected
        startServer { s ->
            readRequest(s)
            // Le serveur prétend que c'est `expected` mais envoie un ciphertext qui ne match pas.
            writeResponse(s, BlockResponseMessage(expected, fragmentIndex = 0, isParity = false, ciphertext = tampered))
        }

        val result = client.downloadBlock(location(expected), timeoutMs = 2_000L)

        assertTrue(result.isFailure)
        assertTrue(
            "expected SecurityException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is SecurityException
        )
    }

    // --- Test 3 : BLOCK_NOT_FOUND ---
    @Test
    fun `downloadBlock fails with IOException when server replies BLOCK_NOT_FOUND`() = runTest {
        val blockId = sha256Hex(ByteArray(8))
        startServer { s ->
            readRequest(s)
            val out = DataOutputStream(s.getOutputStream())
            out.writeByte(BlockTransferChannel.BLOCK_NOT_FOUND.toInt())
            out.flush()
        }

        val result = client.downloadBlock(location(blockId), timeoutMs = 2_000L)

        assertTrue(result.isFailure)
        assertTrue(
            "expected IOException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is IOException
        )
    }

    // --- Test 4 : Socket timeout ---
    @Test
    fun `downloadBlock fails with SocketTimeoutException when server never responds`() = runTest {
        val blockId = sha256Hex(ByteArray(4))
        startServer { s ->
            readRequest(s)
            // Ne répond jamais — la lecture côté client va timeout.
            try { Thread.sleep(2_000) } catch (_: InterruptedException) {}
        }

        val result = client.downloadBlock(location(blockId), timeoutMs = 300L)

        assertTrue(result.isFailure)
        assertTrue(
            "expected SocketTimeoutException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is SocketTimeoutException
        )
    }

    // --- Test 5 : Frame size invalide (négatif) ---
    @Test
    fun `downloadBlock fails with IllegalStateException when response size is invalid`() = runTest {
        val blockId = sha256Hex(ByteArray(4))
        startServer { s ->
            readRequest(s)
            val out = DataOutputStream(s.getOutputStream())
            out.writeByte(BlockTransferChannel.BLOCK_RESPONSE.toInt())
            out.writeInt(-1)
            out.flush()
        }

        val result = client.downloadBlock(location(blockId), timeoutMs = 2_000L)

        assertTrue(result.isFailure)
        assertTrue(
            "expected IllegalStateException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is IllegalStateException
        )
    }
}
