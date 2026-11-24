package com.mobicloud.data.election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.models.electionSignedBytes
import com.mobicloud.domain.models.verifyElectionSignature
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Les messages d'élection traversent le relais en JSON. Le term fait partie des octets
 * signés (format v3) : s'il est perdu en route, le récepteur reconstruit le format v2 et
 * rejette la signature, ce qui cassait toute élection avec term.
 */
class ElectionPayloadJsonTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    private fun signed(type: ElectionMessageType, term: Long): ElectionPayload {
        val ts = 1_800_000_000_000L
        val bytes = electionSignedBytes(type, "node-a", 0.8125f, "cluster-1", ts, term)
        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private); update(bytes); sign()
        }
        return ElectionPayload(
            senderNodeId = "node-a",
            type = type,
            reliabilityScore = 0.8125f,
            signatureBytes = sig,
            clusterId = "cluster-1",
            timestampMs = ts,
            term = term
        )
    }

    private fun verifies(payload: ElectionPayload): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(keyPair.public)
            update(verifyElectionSignature(payload))
            verify(payload.signatureBytes)
        }

    private fun roundTrip(payload: ElectionPayload): ElectionPayload =
        assertNotNullAndGet(electionPayloadFromJson(electionPayloadToJson(payload)))

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value); return value!!
    }

    @Test
    fun `the term survives the relay JSON and the signature still verifies`() {
        for (type in listOf(ElectionMessageType.ELECTION, ElectionMessageType.COORDINATOR)) {
            val received = roundTrip(signed(type, term = 7L))
            assertEquals(7L, received.term)
            assertTrue("signature v3 invalide après le relais pour $type", verifies(received))
        }
    }

    @Test
    fun `a message without term keeps the legacy format and verifies`() {
        val sent = signed(ElectionMessageType.ALIVE, term = 0L)
        val json = JSONObject(electionPayloadToJson(sent).toString(Charsets.UTF_8))
        assertFalse("pas de champ term pour un message v2", json.has("term"))
        val received = roundTrip(sent)
        assertEquals(0L, received.term)
        assertTrue(verifies(received))
    }

    @Test
    fun `JSON from an older version without term is read as term 0`() {
        val legacy = JSONObject(electionPayloadToJson(signed(ElectionMessageType.COORDINATOR, 0L)).toString(Charsets.UTF_8))
        val received = assertNotNullAndGet(electionPayloadFromJson(legacy.toString().toByteArray()))
        assertEquals(0L, received.term)
        assertTrue(verifies(received))
    }

    @Test
    fun `a term altered in transit breaks the signature`() {
        val json = JSONObject(electionPayloadToJson(signed(ElectionMessageType.COORDINATOR, 5L)).toString(Charsets.UTF_8))
        json.put("term", 9L)
        val tampered = assertNotNullAndGet(electionPayloadFromJson(json.toString().toByteArray()))
        assertFalse(verifies(tampered))
    }
}
