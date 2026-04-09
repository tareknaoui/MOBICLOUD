package com.mobicloud.data.p2p

import com.mobicloud.domain.models.NodeIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class UdpHeartbeatReceiverTest {

    @Test
    fun `receiveHeartbeats correctly receives and parses Protobuf identity`() = runTest {
        // Arrange
        val testIdentity = NodeIdentity("receiver_test_id", byteArrayOf(4, 5, 6))
        val protocolBuf = ProtoBuf { }
        val payload = protocolBuf.encodeToByteArray(testIdentity)
        
        // Mock socket that returns one packet then throws timeout
        val mockSocket = object : DatagramSocket() {
            var sent = false
            override fun receive(p: DatagramPacket) {
                if (!sent) {
                    System.arraycopy(payload, 0, p.data, p.offset, payload.size)
                    p.length = payload.size
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
        
        var result: Result<NodeIdentity>? = null
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            result = resultFlow.first()
        }
        
        testScheduler.advanceTimeBy(100)
        
        // Assert
        assertTrue("Result should be success", result != null && result?.isSuccess == true)
        assertEquals(testIdentity, result?.getOrNull())
        
        job.cancel()
    }
}
