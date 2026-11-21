package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Parité par disponibilité cible (réponse aux reviewers sur les seuils 0.5 / 0.8). */
class ParityPolicyTest {

    private fun binom(n: Int, j: Int): Double {
        var r = 1.0
        for (i in 1..j) r = r * (n - j + i) / i
        return r
    }

    /** Forme fermée Σ_{j≥k} C(n,j) pʲ (1−p)ⁿ⁻ʲ pour des hôtes identiques. */
    private fun closedForm(n: Int, k: Int, p: Double) =
        (k..n).sumOf { j -> binom(n, j) * p.pow(j) * (1 - p).pow(n - j) }

    @Test
    fun `availability matches the binomial closed form for identical hosts`() {
        for (p in listOf(0.3, 0.6, 0.9, 0.99)) {
            for (k in 1..6) {
                for (n in k..k + 8) {
                    assertEquals(
                        "n=$n k=$k p=$p",
                        closedForm(n, k, p),
                        ParityPolicy.availability(List(n) { p }, k),
                        1e-12
                    )
                }
            }
        }
    }

    @Test
    fun `availability with heterogeneous hosts matches brute force enumeration`() {
        val scores = listOf(0.95, 0.8, 0.7, 0.55, 0.4, 0.2)
        for (k in 1..scores.size) {
            var expected = 0.0
            for (mask in 0 until (1 shl scores.size)) {
                if (Integer.bitCount(mask) < k) continue
                var prob = 1.0
                scores.forEachIndexed { i, p -> prob *= if (mask and (1 shl i) != 0) p else 1 - p }
                expected += prob
            }
            assertEquals("k=$k", expected, ParityPolicy.availability(scores, k), 1e-12)
        }
    }

    @Test
    fun `availability edge cases`() {
        assertEquals(1.0, ParityPolicy.availability(emptyList(), 0), 0.0)
        assertEquals(0.0, ParityPolicy.availability(listOf(1.0), 2), 0.0)
        assertEquals(1.0, ParityPolicy.availability(listOf(1.0, 1.0, 0.0), 2), 0.0)
        // Scores hors bornes ramenés dans [0, 1]
        assertEquals(1.0, ParityPolicy.availability(listOf(1.7, 3.0), 2), 0.0)
        assertEquals(0.0, ParityPolicy.availability(listOf(-0.5, 0.9), 2), 0.0)
    }

    @Test
    fun `availability never decreases when a host is added`() {
        val pool = listOf(0.9, 0.85, 0.7, 0.6, 0.5, 0.3, 0.1)
        for (k in 1..4) {
            var previous = 0.0
            for (n in k..pool.size) {
                val a = ParityPolicy.availability(pool.take(n), k)
                assertTrue("k=$k n=$n", a >= previous - 1e-15)
                previous = a
            }
        }
    }

    @Test
    fun `minimal parity is the smallest m that reaches the target`() {
        // p = 0.9, k = 4 : A(3) = 0.99727, A(4) = 0.99957
        val d = ParityPolicy.minimalParity(List(20) { 0.9 }, k = 4)
        assertEquals(4, d.parity)
        assertTrue(d.targetMet)
        assertTrue(ParityPolicy.availability(List(4 + d.parity - 1) { 0.9 }, 4) < 0.999)
        assertTrue(d.estimatedAvailability >= 0.999)
    }

    @Test
    fun `very reliable hosts need a single parity fragment`() {
        // p = 0.999, k = 4 : A(1) = 1 − P(≥ 2 pannes sur 5) ≈ 0.99999
        val d = ParityPolicy.minimalParity(List(10) { 0.999 }, k = 4)
        assertEquals(1, d.parity)
        assertTrue(d.targetMet)
    }

    @Test
    fun `a stricter target needs more parity`() {
        val scores = List(30) { 0.8 }
        val loose = ParityPolicy.minimalParity(scores, k = 4, target = 0.99)
        val strict = ParityPolicy.minimalParity(scores, k = 4, target = 0.9999)
        assertTrue(loose.targetMet && strict.targetMet)
        assertTrue(strict.parity > loose.parity)
    }

    @Test
    fun `best effort when too few hosts`() {
        val d = ParityPolicy.minimalParity(List(5) { 0.9 }, k = 4)
        assertEquals(1, d.parity)
        assertFalse(d.targetMet)
        assertEquals(closedForm(5, 4, 0.9), d.estimatedAvailability, 1e-12)
    }

    @Test
    fun `parity is capped at twice k`() {
        val d = ParityPolicy.minimalParity(List(50) { 0.3 }, k = 4)
        assertEquals(ParityPolicy.defaultMaxParity(4), d.parity)
        assertFalse(d.targetMet)
    }

    @Test
    fun `weaker hosts ranked last are accounted for, unlike the weakest-link rule`() {
        // Les K premiers sont excellents mais les suivants faibles : la parité doit compenser
        val scores = listOf(0.99, 0.99, 0.99, 0.99, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4)
        val d = ParityPolicy.minimalParity(scores, k = 4)
        assertTrue(d.parity >= 2)
        assertEquals(ParityPolicy.availability(scores.take(4 + d.parity), 4), d.estimatedAvailability, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `at least k plus one hosts are required`() {
        ParityPolicy.minimalParity(List(4) { 0.9 }, k = 4)
    }
}
