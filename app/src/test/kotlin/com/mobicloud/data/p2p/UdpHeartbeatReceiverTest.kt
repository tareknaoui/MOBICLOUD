package com.mobicloud.data.p2p

import com.mobicloud.domain.models.HeartbeatMessage
import com.mobicloud.domain.models.HeartbeatPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class UdpHeartbeatReceiverTest {

    @Test
    fun `receiveHeartbeats correctly receives and parses Protobuf HeartbeatPayload`() = runTest {
        // Arrange
        val testPayload = HeartbeatPayload(
            nodeId = "receiver_test_id",
            publicKeyBytes = byteArrayOf(4, 5, 6),
            reliabilityScore = 0.8f,
            tcpPort = 9000
        )
        val protocolBuf = ProtoBuf { }
        val encodedPayload = protocolBuf.encodeToByteArray(testPayload)

        // Mock socket qui retourne un paquet puis lance un timeout
        val mockSocket = object : DatagramSocket() {
            var sent = false
            override fun receive(p: DatagramPacket) {
                if (!sent) {
                    System.arraycopy(encodedPayload, 0, p.data, p.offset, encodedPayload.size)
                    p.length = encodedPayload.size
                    p.address = InetAddress.getByName("192.168.1.42")
                    sent = true
                } else {
                    throw java.net.SocketTimeoutException("Timeout")
                }
            }
        }

        val receiver = UdpHeartbeatReceiver(
            protoBuf = protocolBuf,
            socket = mockSocket,
            bufferSize = 1024,
            ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        )

        // Act
        val resultFlow = receiver.receiveHeartbeats()

        var result: Result<HeartbeatMessage>? = null
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            result = resultFlow.first()
        }

        testScheduler.advanceTimeBy(100)

        // Assert
        assertTrue("Result should be success", result != null && result!!.isSuccess)
        val msg = result!!.getOrThrow()
        assertEquals("receiver_test_id", msg.identity.nodeId)
        assertEquals(9000, msg.tcpPort)
        assertEquals("192.168.1.42", msg.senderIp)

        job.cancel()
    }
}
