package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BloomFilterTest {

    @Test
    fun `add then mightContain returns true - no false negatives`() {
        val filter = BloomFilter()
        val blockId = "block-abc-123"
        filter.add(blockId)
        assertTrue(filter.mightContain(blockId))
    }

    @Test
    fun `mightContain on never-added element returns false`() {
        val filter = BloomFilter()
        filter.add("block-xyz")
        // Probabilistically false for a unique element not added
        assertFalse(filter.mightContain("element-jamais-ajoute-z9q8w7"))
    }

    @Test
    fun `toByteArray then fromByteArray reconstructs identical filter`() {
        val original = BloomFilter()
        listOf("block-1", "block-2", "block-3").forEach { original.add(it) }
        val bytes = original.toByteArray()
        val reconstructed = BloomFilter.fromByteArray(bytes)
        listOf("block-1", "block-2", "block-3").forEach { id ->
            assertTrue("Reconstructed filter must contain $id", reconstructed.mightContain(id))
        }
        // A random element not added should not appear in reconstructed either
        assertFalse(reconstructed.mightContain("not-added-zzz999"))
    }

    @Test
    fun `false positive rate under 1 percent for 100 elements and 1000 probes`() {
        // 2048 bits / 3 fonctions → FP théorique ≈ 0.25% pour 100 éléments
        val filter = BloomFilter(bitArraySize = 2048, numHashFunctions = 3)
        val added = (1..100).map { "block-$it" }
        added.forEach { filter.add(it) }

        val addedSet = added.toSet()
        var falsePositives = 0
        val probeCount = 1000
        repeat(probeCount) {
            val probe = UUID.randomUUID().toString()
            if (probe !in addedSet && filter.mightContain(probe)) {
                falsePositives++
            }
        }
        val falsePositiveRate = falsePositives.toDouble() / probeCount
        assertTrue(
            "False positive rate $falsePositiveRate must be < 1%",
            falsePositiveRate < 0.01
        )
    }
}
