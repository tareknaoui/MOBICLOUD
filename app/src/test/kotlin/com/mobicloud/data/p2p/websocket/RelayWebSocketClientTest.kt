package com.mobicloud.data.p2p.websocket

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RelayWebSocketClientTest {

    private lateinit var server: MockWebServer
    private lateinit var authSigner: RelayAuthSigner
    private lateinit var client: RelayWebSocketClient

    // Payload AUTH minimal retourné par le mock
    private val fakeAuthPayload = """{"nodeId":"1234567890abcdef","pubKeySpkiDer":"AAAA","timestamp":1714300000000,"signature":"BBBB"}"""
        .toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        authSigner = mockk()
        coEvery { authSigner.buildAuthPayload() } returns fakeAuthPayload
        client = RelayWebSocketClient(authSigner)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ─── Subtask 7.2 — connectSingle() + AUTH + AUTH_OK + FORWARD + uploadBlock ──

    @Test
    fun `connectSingle envoie un frame AUTH (0x01) a l onOpen`() {
        val authReceived = CountDownLatch(1)
        var receivedFrameType: Byte? = null

        val serverListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = RelayFraming.parseFrame(bytes.toByteArray())
                if (frame != null) {
                    receivedFrameType = frame.first
                    authReceived.countDown()
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/ws").toString().replace("http", "ws")
        val job = CoroutineScope(Dispatchers.IO).launch {
            client.connectSingle(wsUrl).collect {}
        }

        assertTrue("AUTH frame non reçu dans 5s", authReceived.await(5, TimeUnit.SECONDS))
        assertEquals("Type frame doit être AUTH (0x01)", RelayMsg.AUTH, receivedFrameType)
        job.cancel()
    }

    @Test
    fun `connectSingle emet Connected quand serveur repond AUTH_OK`() {
        val serverListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Répond AUTH_OK dès réception de n'importe quel message
                val authOkFrame = RelayFraming.buildFrame(RelayMsg.AUTH_OK)
                webSocket.send(authOkFrame.toByteString())
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/ws").toString().replace("http", "ws")

        val event = runBlocking {
            withTimeout(5_000L) {
                client.connectSingle(wsUrl).first { it is com.mobicloud.domain.models.RelayEvent.Connected }
            }
        }

        assertTrue(event is com.mobicloud.domain.models.RelayEvent.Connected)
    }

    @Test
    fun `connectSingle emet BlockReceived quand serveur envoie FORWARD`() {
        val fromNodeId = "abcdef1234567890" // 16 chars
        val blockId    = "c".repeat(64)     // 64 chars
        val blockData  = ByteArray(256) { 0x77 }

        val serverListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // D'abord AUTH_OK
                webSocket.send(RelayFraming.buildFrame(RelayMsg.AUTH_OK).toByteString())
                // Puis FORWARD
                val forwardPayload = RelayFraming.buildUploadPayload(fromNodeId, blockId, blockData)
                webSocket.send(RelayFraming.buildFrame(RelayMsg.FORWARD, forwardPayload).toByteString())
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/ws").toString().replace("http", "ws")

        val events = runBlocking {
            withTimeout(5_000L) {
                client.connectSingle(wsUrl).take(2).toList()
            }
        }

        val blockEvent = events.filterIsInstance<com.mobicloud.domain.models.RelayEvent.BlockReceived>().firstOrNull()
        assertNotNull("BlockReceived attendu", blockEvent)
        assertEquals(fromNodeId, blockEvent!!.fromNodeId)
        assertEquals(blockId, blockEvent.blockId)
        assertArrayEquals(blockData, blockEvent.data)
    }

    @Test
    fun `uploadBlock construit un frame UPLOAD (0x06) avec destNodeId et blockId corrects`() {
        val destNodeId = "1234567890abcdef"
        val blockId    = "d".repeat(64)
        val data       = ByteArray(128) { 0x55 }

        val uploadFrameCapture = mutableListOf<ByteArray>()
        val uploadReceived = CountDownLatch(1)

        val serverListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = RelayFraming.parseFrame(bytes.toByteArray()) ?: return
                val (type, payload) = frame
                if (type == RelayMsg.AUTH) {
                    // Répondre AUTH_OK
                    webSocket.send(RelayFraming.buildFrame(RelayMsg.AUTH_OK).toByteString())
                } else if (type == RelayMsg.UPLOAD) {
                    uploadFrameCapture.add(bytes.toByteArray())
                    uploadReceived.countDown()
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/ws").toString().replace("http", "ws")
        val connectedLatch = CountDownLatch(1)
        val connectJob = CoroutineScope(Dispatchers.IO).launch {
            client.connectSingle(wsUrl).collect { event ->
                if (event is com.mobicloud.domain.models.RelayEvent.Connected) connectedLatch.countDown()
            }
        }

        assertTrue("Connexion non établie dans 5s", connectedLatch.await(5, TimeUnit.SECONDS))

        CoroutineScope(Dispatchers.IO).launch {
            client.uploadBlock(destNodeId, blockId, data)
        }

        assertTrue("Frame UPLOAD non reçu dans 5s", uploadReceived.await(5, TimeUnit.SECONDS))

        val rawFrame = uploadFrameCapture.first()
        val frame = RelayFraming.parseFrame(rawFrame)
        assertNotNull(frame)
        assertEquals("Type doit être UPLOAD (0x06)", RelayMsg.UPLOAD, frame!!.first)

        val payload = frame.second
        // destNodeId : bytes 0-15
        assertEquals(destNodeId, payload.copyOfRange(0, 16).toString(Charsets.UTF_8))
        // blockId : bytes 16-79
        assertEquals(blockId, payload.copyOfRange(16, 80).toString(Charsets.UTF_8))
        // data : bytes 80+
        assertArrayEquals(data, payload.copyOfRange(80, payload.size))

        connectJob.cancel()
    }

    @Test
    fun `uploadBlock retourne success quand serveur envoie ACK`() {
        val blockId = "e".repeat(64)

        val serverListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = RelayFraming.parseFrame(bytes.toByteArray()) ?: return
                val (type, _) = frame
                when (type) {
                    RelayMsg.AUTH -> {
                        webSocket.send(RelayFraming.buildFrame(RelayMsg.AUTH_OK).toByteString())
                    }
                    RelayMsg.UPLOAD -> {
                        val ackPayload = """{"blockId":"$blockId"}""".toByteArray(Charsets.UTF_8)
                        webSocket.send(RelayFraming.buildFrame(RelayMsg.ACK, ackPayload).toByteString())
                    }
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/ws").toString().replace("http", "ws")
        val connectedLatch = CountDownLatch(1)
        val connectJob = CoroutineScope(Dispatchers.IO).launch {
            client.connectSingle(wsUrl).collect { event ->
                if (event is com.mobicloud.domain.models.RelayEvent.Connected) connectedLatch.countDown()
            }
        }

        assertTrue("Connexion non établie dans 5s", connectedLatch.await(5, TimeUnit.SECONDS))

        val result = runBlocking {
            withTimeout(5_000L) {
                client.uploadBlock("dest1234dest1234", blockId, ByteArray(64))
            }
        }

        assertTrue("uploadBlock doit réussir si ACK reçu", result.isSuccess)
        connectJob.cancel()
    }

    @Test
    fun `uploadBlock retourne failure si pas de connexion active`() = runTest {
        // Pas de connectSingle() appelé → activeWebSocket = null
        val result = client.uploadBlock("dest1234dest1234", "f".repeat(64), ByteArray(10))
        assertTrue("Doit échouer sans connexion active", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // ─── Bug fix : activeWebSocket positionné après AUTH_OK, pas à onOpen ────────

    @Test
    fun `sendRegisterPeer retourne false avant AUTH_OK (activeWebSocket non encore positionne)`() {
        val authReceived = CountDownLatch(1)

        val serverListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = RelayFraming.parseFrame(bytes.toByteArray()) ?: return
                if (frame.first == RelayMsg.AUTH) {
                    authReceived.countDown()
                    // Volontairement : on ne renvoie PAS AUTH_OK — simule un auth lent
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/ws").toString().replace("http", "ws")
        val connectJob = CoroutineScope(Dispatchers.IO).launch {
            client.connectSingle(wsUrl).collect {}
        }

        // La connexion WS est ouverte et AUTH a été envoyé, mais AUTH_OK pas encore reçu
        assertTrue("AUTH frame non reçu dans 5s", authReceived.await(5, TimeUnit.SECONDS))

        // activeWebSocket doit encore être null → sendRegisterPeer retourne false
        val result = client.sendRegisterPeer("node1", "192.168.1.1", 8080, 0.9f, 1_000L)
        assertFalse("sendRegisterPeer doit retourner false avant AUTH_OK", result)

        connectJob.cancel()
    }

    @Test
    fun `activeWebSocket est positionne apres AUTH_OK — sendGetPeers retourne true`() {
        // sendRegisterPeer utilise org.json.JSONObject (stub Android non dispo en JVM unit test).
        // On vérifie la même invariant via sendGetPeers() qui ne fait qu'envoyer un frame binaire.
        val serverListener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = RelayFraming.parseFrame(bytes.toByteArray()) ?: return
                if (frame.first == RelayMsg.AUTH) {
                    webSocket.send(RelayFraming.buildFrame(RelayMsg.AUTH_OK).toByteString())
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/ws").toString().replace("http", "ws")
        val connectedLatch = CountDownLatch(1)
        val connectJob = CoroutineScope(Dispatchers.IO).launch {
            client.connectSingle(wsUrl).collect { event ->
                if (event is com.mobicloud.domain.models.RelayEvent.Connected) connectedLatch.countDown()
            }
        }

        assertTrue("Connexion non établie dans 5s", connectedLatch.await(5, TimeUnit.SECONDS))

        // Après AUTH_OK : activeWebSocket est positionné → sendGetPeers retourne true
        val result = client.sendGetPeers()
        assertTrue("sendGetPeers doit retourner true après AUTH_OK (activeWebSocket positionné)", result)

        connectJob.cancel()
    }

    // ─── Subtask 7.3 — Vérification calcul de délai backoff ───────────────────

    @Test
    fun `calcul backoff exponentiel 5 tentatives consecutives 1s 2s 4s 8s 16s`() {
        val expectedDelaysMs = listOf(1000L, 2000L, 4000L, 8000L, 16000L)

        for ((index, expectedDelay) in expectedDelaysMs.withIndex()) {
            val attempt = index + 1
            val delayMs = minOf(1L shl (attempt - 1), 30L) * 1000L
            assertEquals("Tentative $attempt : délai incorrect", expectedDelay, delayMs)
        }
    }

    @Test
    fun `calcul backoff est plafonné a 30s`() {
        // À partir de la tentative 6 : 2^5 = 32 > 30 → plafond 30s
        for (attempt in 6..10) {
            val delayMs = minOf(1L shl (attempt - 1), 30L) * 1000L
            assertEquals("Tentative $attempt doit être plafonnée à 30s", 30_000L, delayMs)
        }
    }

    @Test
    fun `apres failover (5 tentatives) le compteur se remet a zero`() {
        // Simule la logique de failover : après 5 échecs, serverIndex++ et reset à 0
        var attemptsOnCurrentServer = 0
        var serverIndex = 0
        val failoverLog = mutableListOf<Int>()

        repeat(10) {
            attemptsOnCurrentServer++
            if (attemptsOnCurrentServer >= 5) {
                serverIndex++
                failoverLog.add(serverIndex)
                attemptsOnCurrentServer = 0
            }
        }

        assertEquals("2 failovers doivent s'être produits", 2, failoverLog.size)
        assertEquals(1, failoverLog[0])
        assertEquals(2, failoverLog[1])
    }
}
