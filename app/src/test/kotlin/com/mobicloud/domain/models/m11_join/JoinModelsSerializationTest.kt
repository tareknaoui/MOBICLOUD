package com.mobicloud.domain.models.m11_join

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoinModelsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- JoinRequest ----

    @Test
    fun `JoinRequest round-trip JSON`() {
        val req = JoinRequest(
            senderNodeId = byteArrayOf(1, 2, 3),
            candidatePublicKey = byteArrayOf(4, 5, 6),
            gpsLatitude = 36.7,
            gpsLongitude = 3.08,
            freeBytes = 1_000_000L,
            reliabilityScore = 0.8f,
            timestampMs = 1_700_000_000L,
            signatureBytes = byteArrayOf(7, 8, 9)
        )
        val encoded = json.encodeToString(req)
        val decoded = json.decodeFromString<JoinRequest>(encoded)
        assertArrayEquals(req.senderNodeId, decoded.senderNodeId)
        assertArrayEquals(req.candidatePublicKey, decoded.candidatePublicKey)
        assertEquals(req.gpsLatitude, decoded.gpsLatitude)
        assertEquals(req.timestampMs, decoded.timestampMs)
        assertArrayEquals(req.signatureBytes, decoded.signatureBytes)
    }

    @Test
    fun `JoinRequest sans GPS conserve les nulls`() {
        val req = JoinRequest(
            senderNodeId = byteArrayOf(1),
            candidatePublicKey = byteArrayOf(2),
            gpsLatitude = null, gpsLongitude = null,
            freeBytes = 0L, reliabilityScore = 0.5f,
            timestampMs = 1L, signatureBytes = byteArrayOf(3)
        )
        val decoded = json.decodeFromString<JoinRequest>(json.encodeToString(req))
        assertNull(decoded.gpsLatitude)
        assertNull(decoded.gpsLongitude)
    }

    // ---- JoinResponse.JoinAccept ----

    @Test
    fun `JoinAccept round-trip JSON`() {
        val accept = JoinResponse.JoinAccept(
            clusterId = "cluster-abc",
            superPairNodeId = byteArrayOf(10, 20),
            memberSnapshot = emptyList(),
            timestampMs = 100L,
            signatureBytes = byteArrayOf(30)
        )
        val encoded = json.encodeToString(JoinResponse.serializer(), accept)
        val decoded = json.decodeFromString(JoinResponse.serializer(), encoded) as JoinResponse.JoinAccept
        assertEquals("cluster-abc", decoded.clusterId)
        assertArrayEquals(byteArrayOf(10, 20), decoded.superPairNodeId)
    }

    // ---- JoinResponse.JoinRedirect ----

    @Test
    fun `JoinRedirect round-trip JSON`() {
        val redirect = JoinResponse.JoinRedirect(
            reason = JoinRedirectReason.OUT_OF_RADIUS,
            distanceMeters = 398_000.0,
            alternativeSuperPeers = emptyList(),
            timestampMs = 200L,
            signatureBytes = byteArrayOf(50)
        )
        val encoded = json.encodeToString(JoinResponse.serializer(), redirect)
        val decoded = json.decodeFromString(JoinResponse.serializer(), encoded) as JoinResponse.JoinRedirect
        assertEquals(JoinRedirectReason.OUT_OF_RADIUS, decoded.reason)
        assertEquals(398_000.0, decoded.distanceMeters!!, 0.01)
    }

    // ---- MemberInfo ----

    @Test
    fun `MemberInfo round-trip JSON`() {
        val member = MemberInfo(
            nodeId = byteArrayOf(1, 2),
            publicKey = byteArrayOf(3, 4),
            ipAddress = "10.0.0.1",
            port = 8080,
            gpsLatitude = null, gpsLongitude = null,
            freeBytes = 500L,
            role = MemberRole.MEMBER
        )
        val decoded = json.decodeFromString<MemberInfo>(json.encodeToString(member))
        assertArrayEquals(member.nodeId, decoded.nodeId)
        assertEquals(MemberRole.MEMBER, decoded.role)
    }

    // ---- NodeJoinState equality ----

    @Test
    fun `NodeJoinState Member equality avec ByteArray`() {
        val spId = byteArrayOf(1, 2, 3)
        val s1 = NodeJoinState.Member("c1", spId.copyOf())
        val s2 = NodeJoinState.Member("c1", spId.copyOf())
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }
}
