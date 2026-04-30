package com.mobicloud.data.election

import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Vérifie que StubElectionNetworkClient simule correctement le protocole Bully :
 * - Un pair de score supérieur répond ALIVE à ELECTION → émetteur perd.
 * - Aucun pair supérieur → pas d'ALIVE → émetteur gagne.
 * - COORDINATOR est rebouclé sur incomingMessages.
 */
class StubElectionNetworkClientTest {

    private lateinit var stub: StubElectionNetworkClient

    @Before
    fun setUp() {
        stub = StubElectionNetworkClient()
    }

    private fun electionPayload(nodeId: String, score: Float) = ElectionPayload(
        senderNodeId     = nodeId,
        type             = ElectionMessageType.ELECTION,
        reliabilityScore = score,
        signatureBytes   = ByteArray(0)
    )

    // ── Bully : réponse ALIVE depuis un pair supérieur ──────────────────────────

    @Test
    fun `ELECTION from low-score node triggers ALIVE from higher-score simulated peer`() =
        runTest(UnconfinedTestDispatcher()) {
            stub.addSimulatedPeer(nodeId = "peerHigh", score = 5.0f)

            var received: ElectionPayload? = null
            val job = launch {
                received = stub.incomingMessages.first()
            }

            stub.broadcastElectionMessage(electionPayload("localNode", score = 1.0f))
            job.join()

            assertNotNull("Un ALIVE doit être émis", received)
            assertEquals(ElectionMessageType.ALIVE, received!!.type)
            assertEquals("peerHigh", received!!.senderNodeId)
            assertEquals(5.0f, received!!.reliabilityScore)
        }

    @Test
    fun `ELECTION from high-score node emits no ALIVE from lower-score simulated peer`() =
        runTest(UnconfinedTestDispatcher()) {
            stub.addSimulatedPeer(nodeId = "peerLow", score = 0.5f)

            val received = withTimeoutOrNull(200L) {
                val job = launch { stub.incomingMessages.first() }
                stub.broadcastElectionMessage(electionPayload("localNode", score = 2.0f))
                job.join()
            }

            assertNull("Aucun ALIVE attendu quand le pair local est plus fort", received)
        }

    // ── Bris d'égalité lexicographique ──────────────────────────────────────────

    @Test
    fun `ELECTION tie broken by nodeId lexicographic order — higher ID responds ALIVE`() =
        runTest(UnconfinedTestDispatcher()) {
            stub.addSimulatedPeer(nodeId = "zzzHighId", score = 1.0f)

            var received: ElectionPayload? = null
            val job = launch { received = stub.incomingMessages.first() }

            stub.broadcastElectionMessage(electionPayload("aaaLowId", score = 1.0f))
            job.join()

            assertNotNull(received)
            assertEquals("zzzHighId", received!!.senderNodeId)
        }

    @Test
    fun `ELECTION tie broken by nodeId — lower ID peer does not respond`() =
        runTest(UnconfinedTestDispatcher()) {
            stub.addSimulatedPeer(nodeId = "aaaLowId", score = 1.0f)

            val received = withTimeoutOrNull(200L) {
                val job = launch { stub.incomingMessages.first() }
                stub.broadcastElectionMessage(electionPayload("zzzHighId", score = 1.0f))
                job.join()
            }

            assertNull(received)
        }

    // ── Plusieurs pairs simulés ──────────────────────────────────────────────────

    @Test
    fun `ELECTION with multiple peers — only higher-priority ones emit ALIVE`() =
        runTest(UnconfinedTestDispatcher()) {
            stub.addSimulatedPeer("weakPeer",  score = 0.1f)
            stub.addSimulatedPeer("strongPeer", score = 9.0f)

            val events = mutableListOf<ElectionPayload>()
            val job = launch {
                stub.incomingMessages.collect { events.add(it) }
            }

            stub.broadcastElectionMessage(electionPayload("localNode", score = 1.0f))
            // Petit délai pour que tous les ALIVE soient émis
            kotlinx.coroutines.delay(50)
            job.cancel()

            assertEquals("Seul strongPeer doit avoir répondu", 1, events.size)
            assertEquals("strongPeer", events.first().senderNodeId)
        }

    // ── COORDINATOR rebouclé ─────────────────────────────────────────────────────

    @Test
    fun `COORDINATOR payload is re-emitted on incomingMessages`() =
        runTest(UnconfinedTestDispatcher()) {
            val coordinatorPayload = ElectionPayload(
                senderNodeId     = "winnerNode",
                type             = ElectionMessageType.COORDINATOR,
                reliabilityScore = 8.0f,
                signatureBytes   = ByteArray(0)
            )

            var received: ElectionPayload? = null
            val job = launch { received = stub.incomingMessages.first() }

            stub.broadcastElectionMessage(coordinatorPayload)
            job.join()

            assertNotNull(received)
            assertEquals(ElectionMessageType.COORDINATOR, received!!.type)
            assertEquals("winnerNode", received!!.senderNodeId)
        }

    // ── ALIVE : pas de rebouclage ────────────────────────────────────────────────

    @Test
    fun `ALIVE broadcast does not loop back on incomingMessages`() =
        runTest(UnconfinedTestDispatcher()) {
            val alivePayload = ElectionPayload(
                senderNodeId     = "localNode",
                type             = ElectionMessageType.ALIVE,
                reliabilityScore = 1.0f,
                signatureBytes   = ByteArray(0)
            )

            val received = withTimeoutOrNull(200L) {
                val job = launch { stub.incomingMessages.first() }
                stub.broadcastElectionMessage(alivePayload)
                job.join()
            }

            assertNull("ALIVE ne doit pas être rebouclé", received)
        }

    // ── clearSimulatedPeers ──────────────────────────────────────────────────────

    @Test
    fun `clearSimulatedPeers removes all peers — no ALIVE emitted afterward`() =
        runTest(UnconfinedTestDispatcher()) {
            stub.addSimulatedPeer("strongPeer", score = 9.0f)
            stub.clearSimulatedPeers()

            val received = withTimeoutOrNull(200L) {
                val job = launch { stub.incomingMessages.first() }
                stub.broadcastElectionMessage(electionPayload("localNode", score = 1.0f))
                job.join()
            }

            assertNull("Aucun ALIVE après clearSimulatedPeers", received)
        }

    // ── broadcastElectionMessage retourne toujours success ──────────────────────

    @Test
    fun `broadcastElectionMessage always returns Result success`() = runTest {
        val result = stub.broadcastElectionMessage(electionPayload("node", 1.0f))
        assertTrue(result.isSuccess)
    }
}
