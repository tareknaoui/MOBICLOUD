package com.mobicloud.domain.models

import com.mobicloud.core.format.MobiCloudProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class HelloPayloadTest {

    private fun minimalPayload() = HelloPayload(
        nodeId         = "aabbccddeeff0011",
        publicKeyBytes = byteArrayOf(1, 2, 3, 4),
        tcpPort        = 8080,
        reliabilityScore = 0.9f,
        freeStorageBytes = 1_000_000L
    )

    @Test
    fun `round-trip Protobuf avec GPS`() {
        val original = HelloPayload(
            nodeId           = "aabbccddeeff0011",
            publicKeyBytes   = byteArrayOf(1, 2, 3, 4),
            tcpPort          = 8080,
            reliabilityScore = 0.9f,
            freeStorageBytes = 1_000_000L,
            gpsLatitude      = 36.7538,
            gpsLongitude     = 3.0588,
            superPair        = true
        )
        val bytes   = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), original)
        val decoded = MobiCloudProtoBuf.decodeFromByteArray(HelloPayload.serializer(), bytes)

        assertEquals(original.nodeId, decoded.nodeId)
        assertEquals(36.7538, decoded.gpsLatitude ?: 0.0, 1e-9)
        assertEquals(3.0588,  decoded.gpsLongitude ?: 0.0, 1e-9)
        assertEquals(true, decoded.superPair)
    }

    @Test
    fun `round-trip Protobuf sans GPS - retrocompatibilite`() {
        val original = minimalPayload()
        val bytes   = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), original)
        val decoded = MobiCloudProtoBuf.decodeFromByteArray(HelloPayload.serializer(), bytes)

        assertNull(decoded.gpsLatitude)
        assertNull(decoded.gpsLongitude)
        assertEquals(false, decoded.superPair)
        assertEquals(original.nodeId, decoded.nodeId)
    }

    @Test
    fun `equals et hashCode incluent les champs GPS`() {
        val base = minimalPayload()
        val withGps = base.copy(gpsLatitude = 36.0, gpsLongitude = 3.0)
        assertNotEquals(base, withGps)
        assertNotEquals(base.hashCode(), withGps.hashCode())
    }

    @Test
    fun `equals et hashCode incluent superPair`() {
        val base   = minimalPayload()
        val asSuperPair = base.copy(superPair = true)
        assertNotEquals(base, asSuperPair)
        assertNotEquals(base.hashCode(), asSuperPair.hashCode())
    }
}
