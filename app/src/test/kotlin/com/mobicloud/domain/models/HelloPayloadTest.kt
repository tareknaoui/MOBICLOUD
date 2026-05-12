package com.mobicloud.domain.models

import com.mobicloud.core.format.MobiCloudProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `round-trip Protobuf minimal`() {
        val original = minimalPayload()
        val bytes   = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), original)
        val decoded = MobiCloudProtoBuf.decodeFromByteArray(HelloPayload.serializer(), bytes)

        assertEquals(original.nodeId, decoded.nodeId)
        assertEquals(false, decoded.superPair)
        assertEquals(0, decoded.currentMemberCount)
    }

    @Test
    fun `round-trip Protobuf avec superPair et currentMemberCount`() {
        val original = HelloPayload(
            nodeId           = "aabbccddeeff0011",
            publicKeyBytes   = byteArrayOf(1, 2, 3, 4),
            tcpPort          = 8080,
            reliabilityScore = 0.9f,
            freeStorageBytes = 1_000_000L,
            superPair        = true,
            currentMemberCount = 12
        )
        val bytes   = MobiCloudProtoBuf.encodeToByteArray(HelloPayload.serializer(), original)
        val decoded = MobiCloudProtoBuf.decodeFromByteArray(HelloPayload.serializer(), bytes)

        assertEquals(original.nodeId, decoded.nodeId)
        assertEquals(true, decoded.superPair)
        assertEquals(12, decoded.currentMemberCount)
    }

    @Test
    fun `equals et hashCode incluent superPair`() {
        val base       = minimalPayload()
        val asSuperPair = base.copy(superPair = true)
        assertNotEquals(base, asSuperPair)
        assertNotEquals(base.hashCode(), asSuperPair.hashCode())
    }

    @Test
    fun `equals et hashCode incluent currentMemberCount`() {
        val base  = minimalPayload()
        val with7 = base.copy(currentMemberCount = 7)
        assertNotEquals(base, with7)
        assertNotEquals(base.hashCode(), with7.hashCode())
    }
}
