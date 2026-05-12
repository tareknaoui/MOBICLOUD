package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class HeartbeatModelsSerializationTest {

    private val nodeId = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
    private val sig = byteArrayOf(0x01, 0x02, 0x03)
    private val ts = 1_700_000_000_000L

    // --- Heartbeat ---

    @Test
    fun `Heartbeat round-trip ProtoBuf`() {
        val hb = Heartbeat(nodeId, 1_000_000L, "192.168.1.1", 9090, ts, sig)
        val bytes = ProtoBuf.encodeToByteArray(hb)
        val decoded = ProtoBuf.decodeFromByteArray<Heartbeat>(bytes)
        assertArrayEquals(nodeId, decoded.senderNodeId)
        assertEquals(1_000_000L, decoded.freeBytes)
        assertEquals("192.168.1.1", decoded.ipAddress)
        assertEquals(9090, decoded.port)
        assertEquals(ts, decoded.timestampMs)
        assertArrayEquals(sig, decoded.signatureBytes)
    }

    // --- MemberUpdate JOINED ---

    @Test
    fun `MemberUpdate JOINED round-trip ProtoBuf`() {
        val member = MemberInfo(nodeId, sig, "1.2.3.4", 80, 500L, MemberRole.MEMBER)
        val update = MemberUpdate(MemberUpdateEvent.JOINED, member, byteArrayOf(), ts, sig)
        val bytes = ProtoBuf.encodeToByteArray(update)
        val decoded = ProtoBuf.decodeFromByteArray<MemberUpdate>(bytes)
        assertEquals(MemberUpdateEvent.JOINED, decoded.event)
        assertEquals("1.2.3.4", decoded.member?.ipAddress)
        assertEquals(ts, decoded.timestampMs)
    }

    // --- MemberUpdate LEFT ---

    @Test
    fun `MemberUpdate LEFT round-trip ProtoBuf`() {
        val update = MemberUpdate(MemberUpdateEvent.LEFT, null, nodeId, ts, sig)
        val bytes = ProtoBuf.encodeToByteArray(update)
        val decoded = ProtoBuf.decodeFromByteArray<MemberUpdate>(bytes)
        assertEquals(MemberUpdateEvent.LEFT, decoded.event)
        assertArrayEquals(nodeId, decoded.leftNodeId)
    }

    // --- Leave ---

    @Test
    fun `Leave round-trip ProtoBuf`() {
        val leave = Leave(nodeId, ts, sig)
        val bytes = ProtoBuf.encodeToByteArray(leave)
        val decoded = ProtoBuf.decodeFromByteArray<Leave>(bytes)
        assertArrayEquals(nodeId, decoded.senderNodeId)
        assertEquals(ts, decoded.timestampMs)
        assertArrayEquals(sig, decoded.signatureBytes)
    }

    // --- heartbeatSignedBytes est deterministe ---

    @Test
    fun `heartbeatSignedBytes deux appels identiques produisent memes bytes`() {
        val a = heartbeatSignedBytes(nodeId, 100L, "10.0.0.1", 9090, ts)
        val b = heartbeatSignedBytes(nodeId, 100L, "10.0.0.1", 9090, ts)
        assertArrayEquals(a, b)
    }

    @Test
    fun `memberUpdateSignedBytes est deterministe`() {
        val a = memberUpdateSignedBytes(nodeId, MemberUpdateEvent.JOINED, "aabb", ts)
        val b = memberUpdateSignedBytes(nodeId, MemberUpdateEvent.JOINED, "aabb", ts)
        assertArrayEquals(a, b)
    }

    @Test
    fun `leaveSignedBytes est deterministe`() {
        val a = leaveSignedBytes(nodeId, "cid", ts)
        val b = leaveSignedBytes(nodeId, "cid", ts)
        assertArrayEquals(a, b)
    }

    // H3 régression : un même target nodeId signé par deux SP différents produit des bytes différents
    // → un MEMBER_UPDATE signé par l'ancien SP ne peut pas être rejoué comme signé par le nouveau.
    @Test
    fun `memberUpdateSignedBytes diffère selon le sender`() {
        val spA = byteArrayOf(0xAA.toByte())
        val spB = byteArrayOf(0xBB.toByte())
        val a = memberUpdateSignedBytes(spA, MemberUpdateEvent.LEFT, "cccc", ts)
        val b = memberUpdateSignedBytes(spB, MemberUpdateEvent.LEFT, "cccc", ts)
        assertTrue(!a.contentEquals(b))
    }

    // H4 régression : même node + même ts dans deux clusters différents → bytes différents.
    @Test
    fun `leaveSignedBytes diffère selon le clusterId`() {
        val a = leaveSignedBytes(nodeId, "cluster-A", ts)
        val b = leaveSignedBytes(nodeId, "cluster-B", ts)
        assertTrue(!a.contentEquals(b))
    }

    // C2 régression : ipAddress contenant `|` est rejeté.
    @Test(expected = IllegalArgumentException::class)
    fun `heartbeatSignedBytes rejette pipe dans ipAddress`() {
        heartbeatSignedBytes(nodeId, 100L, "1.2.3.4|attack", 9090, ts)
    }

    // C3 régression : IPv6 inline rejeté en V5.
    @Test(expected = IllegalArgumentException::class)
    fun `heartbeatSignedBytes rejette IPv6`() {
        heartbeatSignedBytes(nodeId, 100L, "fe80::1", 9090, ts)
    }

    // M5 régression : casing hex normalisé en lowercase pour éviter mismatch verify.
    @Test
    fun `memberUpdateSignedBytes normalise hex en lowercase`() {
        val a = memberUpdateSignedBytes(nodeId, MemberUpdateEvent.JOINED, "AABB", ts)
        val b = memberUpdateSignedBytes(nodeId, MemberUpdateEvent.JOINED, "aabb", ts)
        assertArrayEquals(a, b)
    }
}
