package com.mobicloud.domain.models.m11_join

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuperPeerHintTest {

    private val nodeId = byteArrayOf(0x01, 0x02, 0x03)

    private fun hint(nodeId: ByteArray = this.nodeId, lat: Double? = null, lng: Double? = null) =
        SuperPeerHint(nodeId = nodeId, gpsLatitude = lat, gpsLongitude = lng,
            ipAddress = "1.2.3.4", port = 5000, reliabilityScore = 0.9f)

    @Test
    fun `equals et hashCode respectent contentEquals pour nodeId`() {
        val h1 = hint(nodeId.copyOf())
        val h2 = hint(nodeId.copyOf())
        assertEquals(h1, h2)
        assertEquals(h1.hashCode(), h2.hashCode())
    }

    @Test
    fun `deux hints avec nodeId differents ne sont pas egaux`() {
        val h1 = hint(byteArrayOf(0x01))
        val h2 = hint(byteArrayOf(0x02))
        assertNotEquals(h1, h2)
    }

    @Test
    fun `gpsLatitude et gpsLongitude sont null par defaut`() {
        val h = hint()
        assertNull(h.gpsLatitude)
        assertNull(h.gpsLongitude)
    }

    @Test
    fun `hint avec GPS conserve les coordonnees`() {
        val h = hint(lat = 36.7, lng = 3.08)
        assertEquals(36.7, h.gpsLatitude!!, 0.0001)
        assertEquals(3.08, h.gpsLongitude!!, 0.0001)
    }

    @Test
    fun `mapper RelayPeer toSuperPeerHint convertit nodeId hex en ByteArray`() {
        val peer = com.mobicloud.domain.models.RelayPeer(
            nodeId = "0102030405",
            ip = "192.168.1.1",
            port = 9000,
            reliabilityScore = 0.8f,
            lastSeen = 1000L,
            isSuperPair = true,
            clusterId = "cluster-1",
            gpsLatitude = 36.7,
            gpsLongitude = 3.08
        )
        val h = peer.toSuperPeerHint()
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), h.nodeId)
        assertEquals(36.7, h.gpsLatitude!!, 0.0001)
        assertEquals("192.168.1.1", h.ipAddress)
    }

    @Test
    fun `mapper RelayPeer sans GPS produit hint avec GPS null`() {
        val peer = com.mobicloud.domain.models.RelayPeer(
            nodeId = "aabb",
            ip = "10.0.0.1",
            port = 8000,
            reliabilityScore = 0.5f,
            lastSeen = 0L,
            gpsLatitude = null,
            gpsLongitude = null
        )
        val h = peer.toSuperPeerHint()
        assertNull(h.gpsLatitude)
        assertNull(h.gpsLongitude)
    }
}
