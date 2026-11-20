package com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Propriétés ajoutées pour répondre aux reviewers (R1-2, R2-6) :
 * dimensionnement du filtre et indépendance des faux positifs entre rounds grâce au sel.
 */
class BloomFilterSaltTest {

    private fun keys(prefix: String, n: Int) = (0 until n).map { "$prefix-$it" }

    private fun fpRate(filter: BloomFilter, probes: List<String>): Double =
        probes.count { filter.mightContain(it) }.toDouble() / probes.size

    @Test
    fun `legacy fixed filter has about 20 percent false positives with 300 entries`() {
        val legacy = BloomFilter() // 1024 bits, k=3, salt 0
        keys("in", 300).forEach { legacy.add(it) }
        val rate = fpRate(legacy, keys("out", 5000))
        assertTrue("taux legacy attendu ≈ 0.20, obtenu $rate", rate in 0.12..0.28)
    }

    @Test
    fun `sized filter keeps false positives near 1 percent with 300 entries`() {
        val sized = BloomFilter.sized(300, salt = 42L)
        keys("in", 300).forEach { sized.add(it) }
        val rate = fpRate(sized, keys("out", 20000))
        assertTrue("taux attendu ≈ 0.01, obtenu $rate", rate < 0.02)
    }

    @Test
    fun `sized filter never goes below legacy size and respects bounds`() {
        assertEquals(BloomFilter.MIN_BITS, BloomFilter.sized(0).bitArraySize)
        assertEquals(BloomFilter.MIN_BITS, BloomFilter.sized(5).bitArraySize)
        assertTrue(BloomFilter.sized(10_000_000).bitArraySize <= BloomFilter.MAX_BITS)
        assertTrue(BloomFilter.sized(300).numHashFunctions in 1..BloomFilter.MAX_HASH_FUNCTIONS)
    }

    @Test
    fun `salted filter has no false negatives and survives serialization`() {
        val f = BloomFilter.sized(200, salt = -7L)
        val items = keys("x", 200)
        items.forEach { f.add(it) }
        val copy = BloomFilter.fromByteArray(f.toByteArray(), f.bitArraySize, f.numHashFunctions, f.salt)
        assertTrue(items.all { copy.mightContain(it) })
    }

    /**
     * Cœur de la critique R1-2 : avec un hachage déterministe, un faux positif se répète
     * à chaque round (le filtre reconstruit est identique). Avec un sel par round, un
     * élément faussement positif au round 1 ne l'est plus qu'avec une probabilité ≈ fp.
     */
    @Test
    fun `false positives persist without salt but are independent across salted rounds`() {
        val members = keys("member", 80)
        val probes = keys("probe", 4000)

        // Petit filtre volontairement saturé pour obtenir beaucoup de faux positifs
        fun build(salt: Long) = BloomFilter(256, 2, salt).also { f -> members.forEach { f.add(it) } }

        // Sans sel : le filtre reconstruit au round suivant masque exactement les mêmes éléments
        val legacyRound1 = probes.filter { build(0L).mightContain(it) }
        val legacyRound2 = legacyRound1.filter { build(0L).mightContain(it) }
        assertEquals(legacyRound1.size, legacyRound2.size)

        // Avec sel : les éléments masqués au round 1 ne le restent qu'avec une probabilité ≈ fp
        val round1 = build(1111L)
        val round2 = build(2222L)
        val fp = fpRate(round1, probes)
        val maskedRound1 = probes.filter { round1.mightContain(it) }
        val stillMasked = maskedRound1.count { round2.mightContain(it) }.toDouble() / maskedRound1.size
        assertTrue("fp de base trop faible pour le test : $fp", fp > 0.1)
        assertTrue(
            "persistance attendue ≈ fp ($fp), obtenue $stillMasked",
            stillMasked < fp + 0.1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromByteArray rejects an oversized filter announced by a peer`() {
        BloomFilter.fromByteArray(ByteArray(8), size = BloomFilter.MAX_BITS + 1, k = 3)
    }
}
