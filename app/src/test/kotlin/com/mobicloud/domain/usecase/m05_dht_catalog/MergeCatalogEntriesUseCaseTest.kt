package com.mobicloud.domain.usecase.m05_dht_catalog

import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.FragmentLocation
import org.junit.Assert.assertEquals

import org.junit.Test

class MergeCatalogEntriesUseCaseTest {

    private val mergeUseCase = MergeCatalogEntriesUseCase()

    @Test
    fun testMergeLWW_newerVersionWins() {
        val oldEntry = CatalogEntry("hash1", "pub1", 1L, emptyList())
        val newEntry = CatalogEntry("hash1", "pub1", 2L, emptyList())

        val result1 = mergeUseCase(oldEntry, newEntry)
        val result2 = mergeUseCase(newEntry, oldEntry)

        assertEquals(2L, result1.versionClock)
        assertEquals(2L, result2.versionClock)
        // Commutativity: A U B == B U A
        assertEquals(result1, result2)
    }

    @Test
    fun testMerge_identicalVersions_mergesFragments_commutativity() {
        val f1 = FragmentLocation(0, "hash_f1", listOf("nodeA"))
        val f2 = FragmentLocation(1, "hash_f2", listOf("nodeB"))

        val entry1 = CatalogEntry("hash1", "pub1", 1L, listOf(f1))
        val entry2 = CatalogEntry("hash1", "pub2", 1L, listOf(f2))

        val result1 = mergeUseCase(entry1, entry2)
        val result2 = mergeUseCase(entry2, entry1)

        assertEquals(result1, result2) // Commutativity A U B == B U A
        assertEquals(2, result1.fragmentLocations.size)
        // Check deterministic tie breaker (lexicographical)
        assertEquals("pub2", result1.ownerPubKeyHash)
    }
}
