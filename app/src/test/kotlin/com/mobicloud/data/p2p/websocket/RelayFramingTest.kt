package com.mobicloud.data.p2p.websocket

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RelayFramingTest {

    // ─── buildFrame ────────────────────────────────────────────────────────────

    @Test
    fun `buildFrame produit un header 5-bytes correct suivi du payload`() {
        val payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val frame = RelayFraming.buildFrame(0x01, payload)

        assertEquals(8, frame.size) // 5 header + 3 payload
        assertEquals(0x01.toByte(), frame[0]) // type
        // length LE = 3
        val length = ByteBuffer.wrap(frame, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(3, length)
        // payload bytes
        assertArrayEquals(payload, frame.copyOfRange(5, 8))
    }

    @Test
    fun `buildFrame sans payload produit un frame de 5 bytes`() {
        val frame = RelayFraming.buildFrame(RelayMsg.GET_PEERS)
        assertEquals(5, frame.size)
        assertEquals(RelayMsg.GET_PEERS, frame[0])
        val length = ByteBuffer.wrap(frame, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(0, length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildFrame leve une exception si payload depasse MAX_PAYLOAD_SIZE`() {
        RelayFraming.buildFrame(0x01, ByteArray(RelayFraming.MAX_PAYLOAD_SIZE + 1))
    }

    // ─── parseFrame ────────────────────────────────────────────────────────────

    @Test
    fun `parseFrame reconstruit type et payload depuis buildFrame (round-trip)`() {
        val type: Byte = RelayMsg.UPLOAD
        val payload = ByteArray(100) { it.toByte() }
        val frame = RelayFraming.buildFrame(type, payload)

        val result = RelayFraming.parseFrame(frame)
        assertNotNull(result)
        assertEquals(type, result!!.first)
        assertArrayEquals(payload, result.second)
    }

    @Test
    fun `parseFrame retourne null si frame trop courte (moins de 5 bytes)`() {
        assertNull(RelayFraming.parseFrame(ByteArray(4)))
        assertNull(RelayFraming.parseFrame(ByteArray(0)))
    }

    @Test
    fun `parseFrame retourne null si length ne correspond pas a la taille reelle`() {
        // Frame avec length=10 mais seulement 3 bytes de payload
        val frame = ByteArray(8) // 5 header + 3 payload
        frame[0] = 0x01
        ByteBuffer.wrap(frame, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(10) // mensonge
        assertNull(RelayFraming.parseFrame(frame))
    }

    @Test
    fun `parseFrame retourne null si length est negatif`() {
        val frame = ByteArray(5)
        frame[0] = 0x01
        ByteBuffer.wrap(frame, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(-1)
        assertNull(RelayFraming.parseFrame(frame))
    }

    // ─── buildUploadPayload ────────────────────────────────────────────────────

    @Test
    fun `buildUploadPayload place destNodeId en offset 0-15 blockId en 16-79 data en 80+`() {
        val destNodeId = "a3f2b1c0d4e5f601" // 16 chars
        val blockId = "a".repeat(64)          // 64 chars hex SHA-256
        val data = ByteArray(1024) { 0x42 }

        val payload = RelayFraming.buildUploadPayload(destNodeId, blockId, data)

        assertEquals(80 + 1024, payload.size)
        // destNodeId : bytes 0-15
        assertEquals(destNodeId, payload.copyOfRange(0, 16).toString(Charsets.UTF_8))
        // blockId : bytes 16-79
        assertEquals(blockId, payload.copyOfRange(16, 80).toString(Charsets.UTF_8))
        // data : bytes 80+
        assertArrayEquals(data, payload.copyOfRange(80, payload.size))
    }

    @Test
    fun `buildUploadPayload padde destNodeId court avec zeros`() {
        val destNodeId = "abc" // moins de 16 chars
        val blockId = "f".repeat(64)
        val data = ByteArray(10)

        val payload = RelayFraming.buildUploadPayload(destNodeId, blockId, data)

        // Les 3 premiers bytes = "abc", le reste = 0x00
        assertEquals('a', payload[0].toInt().toChar())
        assertEquals('b', payload[1].toInt().toChar())
        assertEquals('c', payload[2].toInt().toChar())
        assertEquals(0x00.toByte(), payload[3])
        assertEquals(0x00.toByte(), payload[15])
    }

    // ─── parseForwardPayload ───────────────────────────────────────────────────

    @Test
    fun `parseForwardPayload extrait correctement fromNodeId blockId et data`() {
        val fromNodeId = "1234567890abcdef" // 16 chars
        val blockId    = "b".repeat(64)     // 64 chars
        val data       = ByteArray(512) { 0x77 }

        val payload = ByteArray(80 + 512)
        fromNodeId.toByteArray().copyInto(payload, 0)
        blockId.toByteArray().copyInto(payload, 16)
        data.copyInto(payload, 80)

        val result = RelayFraming.parseForwardPayload(payload)
        assertNotNull(result)
        assertEquals(fromNodeId, result!!.first)
        assertEquals(blockId, result.second)
        assertArrayEquals(data, result.third)
    }

    @Test
    fun `parseForwardPayload retourne null si payload inferieur a 80 bytes`() {
        assertNull(RelayFraming.parseForwardPayload(ByteArray(79)))
    }

    @Test
    fun `parseForwardPayload retourne null si blockId n est pas 64 chars`() {
        val payload = ByteArray(80 + 10) // blockId vide → longueur 0 ≠ 64
        "nodeId16bytesXXX".toByteArray().copyInto(payload, 0)
        // blockId laissé vide → length != 64
        assertNull(RelayFraming.parseForwardPayload(payload))
    }
}
