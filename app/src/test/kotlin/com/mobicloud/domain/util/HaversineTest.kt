package com.mobicloud.domain.util

import com.mobicloud.domain.models.GpsCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaversineTest {

    private fun coord(lat: Double, lng: Double) =
        GpsCoordinate(lat, lng, 50f, System.currentTimeMillis())

    @Test
    fun `Alger vers Oran est environ 354 km`() {
        val alger = coord(36.7538, 3.0588)
        val oran  = coord(35.6969, -0.6331)
        val dist  = Haversine.distanceMeters(alger, oran)
        val expected = 354_000.0
        assertTrue(
            "Attendu ~354 km, obtenu ${dist / 1000} km",
            dist in expected * 0.98..expected * 1.02
        )
    }

    @Test
    fun `Bab Ezzouar vers Centre Alger est environ 12 km`() {
        // Distance Haversine pure (orthodromique) entre Bab Ezzouar (banlieue Est) et le
        // centre d'Alger. La valeur réelle calculée est ~11.96 km — l'estimation routière
        // (~13 km) inclut les détours réseau, mais le test mesure la distance à vol d'oiseau.
        val babEzzouar   = coord(36.7218, 3.1869)
        val centreAlger  = coord(36.7538, 3.0588)
        val dist         = Haversine.distanceMeters(babEzzouar, centreAlger)
        val expected     = 12_000.0
        assertTrue(
            "Attendu ~12 km, obtenu ${dist / 1000} km",
            dist in expected * 0.95..expected * 1.05
        )
    }

    @Test
    fun `point vers lui-meme est 0`() {
        val p = coord(36.7538, 3.0588)
        assertEquals(0.0, Haversine.distanceMeters(p, p), 0.0)
    }

    @Test
    fun `symetrie distance(a,b) == distance(b,a)`() {
        val a = coord(36.7538, 3.0588)
        val b = coord(35.6969, -0.6331)
        assertEquals(
            Haversine.distanceMeters(a, b),
            Haversine.distanceMeters(b, a),
            1e-6
        )
    }

    @Test
    fun `points quasi-antipodiaux ne produisent pas NaN - fix F3`() {
        // Pôle Nord vs Pôle Sud : cas extrême qui peut causer h > 1.0 par arrondi flottant
        val nord = coord(90.0, 0.0)
        val sud  = coord(-90.0, 0.0)
        val dist = Haversine.distanceMeters(nord, sud)
        assertTrue("Distance pôles doit être ~20 015 km, obtenu ${dist / 1000} km", dist.isFinite())
        assertTrue("Distance pôles doit être > 10 000 km", dist > 10_000_000.0)
    }
}
