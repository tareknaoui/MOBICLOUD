package com.mobicloud.domain.models.m11_join

import com.mobicloud.domain.models.RelayPeer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SuperPeerHintTest {

    private val nodeId = byteArrayOf(0x01, 0x02, 0x03)

    private fun hint(
        nodeId: ByteArray = this.nodeId,
        memberCount: Int = 0
    ) = SuperPeerHint(
        nodeId = nodeId,
        ipAddress = "1.2.3.4",
        port = 5000,
        reliabilityScore = 0.9f,
        currentMemberCount = memberCount
    )

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
    fun `currentMemberCount est 0 par defaut`() {
        val h = hint()
        assertEquals(0, h.currentMemberCount)
    }

    @Test
    fun `hint avec currentMemberCount conserve la valeur`() {
        val h = hint(memberCount = 12)
        assertEquals(12, h.currentMemberCount)
    }

    @Test
    fun `deux hints avec currentMemberCount differents ne sont pas egaux`() {
        val h1 = hint(memberCount = 5)
        val h2 = hint(memberCount = 10)
        assertNotEquals(h1, h2)
    }

    @Test
    fun `mapper RelayPeer toSuperPeerHint convertit nodeId hex en ByteArray`() {
        val peer = RelayPeer(
            nodeId = "0102030405",
            ip = "192.168.1.1",
            port = 9000,
            reliabilityScore = 0.8f,
            lastSeen = 1000L,
            isSuperPair = true,
            clusterId = "cluster-1",
            currentMemberCount = 7
        )
        val h = peer.toSuperPeerHint()
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), h.nodeId)
        assertEquals(7, h.currentMemberCount)
        assertEquals("192.168.1.1", h.ipAddress)
    }

    @Test
    fun `mapper RelayPeer sans currentMemberCount produit hint avec 0`() {
        val peer = RelayPeer(
            nodeId = "aabb",
            ip = "10.0.0.1",
            port = 8000,
            reliabilityScore = 0.5f,
            lastSeen = 0L
        )
        val h = peer.toSuperPeerHint()
        assertEquals(0, h.currentMemberCount)
    }
}
