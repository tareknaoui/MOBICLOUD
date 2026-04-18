package com.mobicloud.domain.usecase.m05_dht_catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistentHashRingTest {

    @Test
    fun testDeterminismSameBlockIdProducedSamePartition() {
        val ring = ConsistentHashRing(listOf("node1", "node2", "node3"))
        val blockId = "block-xyz-123"

        val partition1 = ring.getPartition(blockId)
        val partition2 = ring.getPartition(blockId)

        assertEquals(partition1, partition2)
    }

    @Test
    fun testHashWithinValidRange() {
        val nodes = listOf("node1", "node2", "node3", "node4", "node5")
        val ring = ConsistentHashRing(nodes)

        for (i in 0..99) {
            val partition = ring.getPartition("block-$i")
            assertTrue("Partition should be one of the nodes", partition in nodes)
        }
    }

    @Test
    fun testPartitionChangeWithNodeCountChange() {
        val blockId = "block-abc-456"

        val ring3Nodes = ConsistentHashRing(listOf("node1", "node2", "node3"))
        val partition3 = ring3Nodes.getPartition(blockId)

        val ring5Nodes = ConsistentHashRing(listOf("node1", "node2", "node3", "node4", "node5"))
        val partition5 = ring5Nodes.getPartition(blockId)

        // Both must be valid assignments
        assertTrue(partition3 in listOf("node1", "node2", "node3"))
        assertTrue(partition5 in listOf("node1", "node2", "node3", "node4", "node5"))
    }

    @Test
    fun testCrossNodeValidation() {
        val nodes = listOf("node1", "node2", "node3")
        val ring1 = ConsistentHashRing(nodes)
        val ring2 = ConsistentHashRing(nodes)

        val blockIds = listOf("block-001", "block-002", "block-003", "block-xyz")

        for (blockId in blockIds) {
            val partition1 = ring1.getPartition(blockId)
            val partition2 = ring2.getPartition(blockId)
            assertEquals("Two ring instances must produce same partition for $blockId", partition1, partition2)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyNodesThrowsException() {
        ConsistentHashRing(emptyList())
    }

    @Test
    fun testSingleNodeAlwaysReturnsItself() {
        val ring = ConsistentHashRing(listOf("onlyNode"))

        val partition1 = ring.getPartition("block-1")
        val partition2 = ring.getPartition("block-2")
        val partition3 = ring.getPartition("block-3")

        assertEquals("onlyNode", partition1)
        assertEquals("onlyNode", partition2)
        assertEquals("onlyNode", partition3)
    }

    @Test
    fun testDistributionAcrossMultipleNodes() {
        val nodes = listOf("node1", "node2", "node3", "node4", "node5")
        val ring = ConsistentHashRing(nodes)

        val partitions = mutableMapOf<String, Int>()
        for (i in 0..999) {
            val partition = ring.getPartition("block-$i")
            partitions[partition] = (partitions[partition] ?: 0) + 1
        }

        // All nodes should have at least some blocks
        val allNodesPresent = nodes.all { it in partitions }
        assertTrue("All nodes should have at least some blocks assigned", allNodesPresent)

        // Distribution should be roughly even (allow some variance)
        val avgBlocksPerNode = 1000 / nodes.size
        partitions.values.forEach { count ->
            val deviation = Math.abs(count - avgBlocksPerNode).toDouble() / avgBlocksPerNode
            assertTrue("Distribution should be relatively even (deviation: $deviation)", deviation < 0.5)
        }
    }
}
