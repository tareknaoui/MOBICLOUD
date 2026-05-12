package com.mobicloud.domain.models.m11_join

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeJoinStateTest {

    @Test
    fun `Undiscovered est un data object singleton`() {
        val s1: NodeJoinState = NodeJoinState.Undiscovered
        val s2: NodeJoinState = NodeJoinState.Undiscovered
        assertEquals(s1, s2)
    }

    @Test
    fun `Joining egalite par targetSuperPair et attemptIndex`() {
        val hint = SuperPeerHint(byteArrayOf(1), ipAddress = "1.2.3.4", port = 5000, reliabilityScore = 0.9f)
        val j1 = NodeJoinState.Joining(hint, 0)
        val j2 = NodeJoinState.Joining(hint, 0)
        assertEquals(j1, j2)
    }

    @Test
    fun `Member egalite ByteArray contentEquals`() {
        val id = byteArrayOf(0xAA.toByte())
        val m1 = NodeJoinState.Member("c1", id.copyOf())
        val m2 = NodeJoinState.Member("c1", id.copyOf())
        assertEquals(m1, m2)
    }

    @Test
    fun `Member inegalite si superPairNodeId different`() {
        val m1 = NodeJoinState.Member("c1", byteArrayOf(0x01))
        val m2 = NodeJoinState.Member("c1", byteArrayOf(0x02))
        assertNotEquals(m1, m2)
    }

    @Test
    fun `SuperPair egalite par clusterId`() {
        assertEquals(NodeJoinState.SuperPair("cid-1"), NodeJoinState.SuperPair("cid-1"))
        assertNotEquals(NodeJoinState.SuperPair("cid-1"), NodeJoinState.SuperPair("cid-2"))
    }

    @Test
    fun `Rejoining contient la raison`() {
        val r = NodeJoinState.Rejoining(RejoinReason.SP_TIMEOUT, "cid-test")
        assertEquals(RejoinReason.SP_TIMEOUT, r.reason)
        assertEquals("cid-test", r.clusterId)
    }

    @Test
    fun `Isolated conserve rejectionCount et timestamp`() {
        val iso = NodeJoinState.Isolated(3, 9_000L)
        assertEquals(3, iso.rejectionCount)
        assertEquals(9_000L, iso.lastRejectionTimeMs)
    }

    @Test
    fun `6 sous-types distincts couverts`() {
        val states: List<NodeJoinState> = listOf(
            NodeJoinState.Undiscovered,
            NodeJoinState.Joining(SuperPeerHint(byteArrayOf(1), ipAddress = "x", port = 1, reliabilityScore = 0f), 0),
            NodeJoinState.Member("c", byteArrayOf(2)),
            NodeJoinState.SuperPair("c"),
            NodeJoinState.Rejoining(RejoinReason.SP_ABDICATION, "c"),
            NodeJoinState.Isolated(0, 0L)
        )
        assertEquals(6, states.size)
        // Tous distincts
        states.forEachIndexed { i, s ->
            states.forEachIndexed { j, t ->
                if (i != j) assertTrue("$s == $t inattendu", s != t)
            }
        }
    }
}
