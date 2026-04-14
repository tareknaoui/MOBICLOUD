package com.mobicloud.data.p2p

import com.mobicloud.domain.models.HeartbeatPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
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
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class UdpHeartbeatBroadcasterTest {

    private fun makePayload(nodeId: String = "test_id") = HeartbeatPayload(
        nodeId = nodeId,
        publicKeyBytes = byteArrayOf(1, 2, 3),
        reliabilityScore = 1.0f,
        tcpPort = 8080
    )

    @Test
    fun `broadcastLoop correctly formats and sends Protobuf HeartbeatPayload over UDP periodically`() = runTest {
        // Arrange
        val testPayload = makePayload()
        val protocolBuf = ProtoBuf { }
        val expectedBytes = protocolBuf.encodeToByteArray(testPayload)

        val sentPackets = CopyOnWriteArrayList<DatagramPacket>()
        val mockSocket = object : DatagramSocket() {
            override fun send(p: DatagramPacket) {
                val dataCopy = p.data.copyOfRange(p.offset, p.offset + p.length)
                val packetCopy = DatagramPacket(dataCopy, dataCopy.size, p.address, p.port)
                sentPackets.add(packetCopy)
            }
        }

        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)

        val broadcaster = UdpHeartbeatBroadcaster(
            protoBuf = protocolBuf,
            socket = mockSocket,
            multicastAddress = "127.0.0.1",
            port = 4545,
            initialIntervalMs = 100L,
            ioDispatcher = testDispatcher
        )

        // Act
        val job = launch(testDispatcher) {
            broadcaster.startBroadcasting(testPayload)
        }

        testScheduler.advanceTimeBy(350)
        testScheduler.runCurrent()
        job.cancelAndJoin()

        // Assert
        assertTrue("Should have sent multiple packets", sentPackets.size >= 2)

        val firstPacket = sentPackets[0]
        assertEquals("Port should match configured port", 4545, firstPacket.port)
        assertTrue("Content should match expected protobuf payload", expectedBytes.contentEquals(firstPacket.data))
    }

    @Test
    fun `broadcastLoop uses exponential backoff for delays up to max threshold`() = runTest {
        // Arrange
        val testPayload = makePayload()
        val protocolBuf = ProtoBuf { }

        val sentTimes = CopyOnWriteArrayList<Long>()
        val mockSocket = object : DatagramSocket() {
            override fun send(p: DatagramPacket) {
                sentTimes.add(testScheduler.currentTime)
            }
        }

        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)

        val broadcaster = UdpHeartbeatBroadcaster(
            protoBuf = protocolBuf,
            socket = mockSocket,
            multicastAddress = "127.0.0.1",
            port = 4545,
            initialIntervalMs = 1000L,
            maxIntervalMs = 4000L,
            backoffFactor = 2.0,
            ioDispatcher = testDispatcher
        )

        // Act
        val job = launch(testDispatcher) {
            broadcaster.startBroadcasting(testPayload)
        }

        // Let it run for 1 + 2 + 4 + 4 + 4 = 15 seconds total
        testScheduler.advanceTimeBy(16000)
        job.cancelAndJoin()

        // Assert: séquence de timestamps attendue avec backoff exponentiel plafonné à 4000ms
        assertTrue("Should have sent multiple packets", sentTimes.size >= 5)
        assertEquals("Packet 0: T=0 (initial burst)", 0L, sentTimes[0])
        assertEquals("Packet 1: T=1000 (after 1000ms delay)", 1000L, sentTimes[1])
        assertEquals("Packet 2: T=3000 (after 2000ms delay)", 3000L, sentTimes[2])
        assertEquals("Packet 3: T=7000 (after 4000ms delay — capped)", 7000L, sentTimes[3])
        assertEquals("Packet 4: T=11000 (after 4000ms delay)", 11000L, sentTimes[4])
        assertEquals("Packet 5: T=15000 (after 4000ms delay)", 15000L, sentTimes[5])
    }

    /**
     * F-08: resetBackoff() interrompt le délai courant et la boucle repart de initialIntervalMs.
     */
    @Test
    fun `resetBackoff interrupts current delay and restarts from initialIntervalMs`() = runTest {
        // Arrange
        val testPayload = makePayload()
        val protocolBuf = ProtoBuf { }

        val sentTimes = CopyOnWriteArrayList<Long>()
        val mockSocket = object : DatagramSocket() {
            override fun send(p: DatagramPacket) {
                sentTimes.add(testScheduler.currentTime)
            }
        }

        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)

        val broadcaster = UdpHeartbeatBroadcaster(
            protoBuf = protocolBuf,
            socket = mockSocket,
            multicastAddress = "127.0.0.1",
            port = 4545,
            initialIntervalMs = 1000L,
            maxIntervalMs = 4000L,
            backoffFactor = 2.0,
            ioDispatcher = testDispatcher
        )

        val job = launch(testDispatcher) {
            broadcaster.startBroadcasting(testPayload)
        }

        // Phase 1: avance jusqu'à T=3000 → 3 paquets envoyés (T=0, T=1000, T=3000)
        testScheduler.advanceTimeBy(3500)
        testScheduler.runCurrent()

        assertTrue("Before reset: should have 3 packets (T=0,1000,3000)", sentTimes.size >= 3)
        assertEquals(0L, sentTimes[0])
        assertEquals(1000L, sentTimes[1])
        assertEquals(3000L, sentTimes[2])

        // Phase 2: reset du backoff au milieu du délai de 4000ms (à T=3500)
        broadcaster.resetBackoff()
        testScheduler.runCurrent()

        assertTrue("Reset packet sent at T=3500: sentTimes=$sentTimes", sentTimes.contains(3500L))

        // Phase 3: après reset, backoff repart de 1000ms
        testScheduler.advanceTimeBy(5000)
        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertTrue("T=4500 expected after 1000ms restart: sentTimes=$sentTimes", sentTimes.contains(4500L))
        assertTrue("T=6500 expected after 2000ms backoff: sentTimes=$sentTimes", sentTimes.contains(6500L))
    }

    /**
     * F-11: coerceAtMost() protège contre un backoffFactor extrême.
     */
    @Test
    fun `backoff delay is strictly capped at maxIntervalMs even with extreme backoff factor`() = runTest {
        // Arrange
        val testPayload = makePayload()
        val protocolBuf = ProtoBuf { }

        val sentTimes = CopyOnWriteArrayList<Long>()
        val mockSocket = object : DatagramSocket() {
            override fun send(p: DatagramPacket) {
                sentTimes.add(testScheduler.currentTime)
            }
        }

        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)

        val broadcaster = UdpHeartbeatBroadcaster(
            protoBuf = protocolBuf,
            socket = mockSocket,
            multicastAddress = "127.0.0.1",
            port = 4545,
            initialIntervalMs = 1000L,
            maxIntervalMs = 8000L,
            backoffFactor = 1e10,
            ioDispatcher = testDispatcher
        )

        val job = launch(testDispatcher) {
            broadcaster.startBroadcasting(testPayload)
        }

        testScheduler.advanceTimeBy(10000)
        job.cancelAndJoin()

        assertTrue("Should have sent at least 3 packets", sentTimes.size >= 3)
        assertEquals("Packet 0 at T=0", 0L, sentTimes[0])
        assertEquals("Packet 1 at T=1000", 1000L, sentTimes[1])
        assertEquals("Packet 2 capped at T=9000 (not T=${1000L + (1000L * 1e10).toLong()})", 9000L, sentTimes[2])
    }

    /**
     * F-07: Le constructeur rejette initialIntervalMs=0.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws IllegalArgumentException when initialIntervalMs is zero`() {
        UdpHeartbeatBroadcaster(
            protoBuf = ProtoBuf { },
            socket = object : DatagramSocket() {},
            multicastAddress = "127.0.0.1",
            port = 4545,
            initialIntervalMs = 0L
        )
    }
}
