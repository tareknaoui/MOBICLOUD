package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.p2p.join.JoinNetworkClientImpl
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.JoinSubType
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import com.mobicloud.domain.repository.JoinIncomingMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinNetworkClientImplTest {

    private lateinit var relayWebSocketClient: RelayWebSocketClient
    private lateinit var client: JoinNetworkClientImpl
    private val dispatcher = UnconfinedTestDispatcher()

    private val hint = SuperPeerHint(
        nodeId = byteArrayOf(0x01, 0x02),
        ipAddress = "1.2.3.4",
        port = 5000,
        reliabilityScore = 0.9f
    )

    @Before
    fun setup() {
        relayWebSocketClient = mockk(relaxed = true)
        coEvery { relayWebSocketClient.uploadBlock(any(), any(), any()) } returns Result.success(Unit)
        client = JoinNetworkClientImpl(relayWebSocketClient)
    }

    @Test
    fun `onRelayMessage propage le message vers incomingJoinRequests`() = runTest(dispatcher) {
        val msg = JoinIncomingMessage("nodeA", JoinSubType.JOIN_REQUEST.byte, byteArrayOf(0x01))
        val received = mutableListOf<JoinIncomingMessage>()
        val job = launch(dispatcher) {
            client.incomingJoinRequests.collect { received.add(it) }
        }
        client.onRelayMessage(msg)
        job.cancel()
        assertTrue(received.contains(msg))
    }

    @Test
    fun `sendJoinRequest encode avec prefixe magic + JoinSubType et appelle uploadBlock`() = runTest(dispatcher) {
        val payloadSlot = slot<ByteArray>()
        coEvery { relayWebSocketClient.uploadBlock(any(), any(), capture(payloadSlot)) } returns Result.success(Unit)

        val request = JoinRequest(
            senderNodeId = byteArrayOf(0x01),
            candidatePublicKey = byteArrayOf(0x02),
            freeBytes = 1000L, reliabilityScore = 0.8f,
            timestampMs = 1L, signatureBytes = byteArrayOf(0x03)
        )

        // Lancer le sendJoinRequest dans un job séparé car il attend une réponse
        val sendJob = launch(dispatcher) {
            // La réponse ne viendra pas → timeout → failure (attendu en test)
            client.sendJoinRequest(hint, request)
        }

        // Vérifier que uploadBlock a été appelé avec préfixe 0x04
        coVerify(timeout = 1000) { relayWebSocketClient.uploadBlock(any(), any(), any()) }
        sendJob.cancel()

        // Préfixe magic 0xFF + JoinSubType.JOIN_REQUEST (élimine collision avec BlockReceived).
        // assertion non-conditionnelle : si payload n'est pas capturé, le test doit échouer.
        assertTrue("uploadBlock payload doit être capturé", payloadSlot.isCaptured)
        assertEquals(0xFF.toByte(), payloadSlot.captured[0])
        assertEquals(JoinSubType.JOIN_REQUEST.byte, payloadSlot.captured[1])
    }
}
