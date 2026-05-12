package com.mobicloud.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GpsCoordinateTest {

    @Test(expected = IllegalArgumentException::class)
    fun `latitude 91 leve IllegalArgumentException`() {
        GpsCoordinate(latitude = 91.0, longitude = 0.0, accuracyMeters = 10f, timestampMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `latitude -91 leve IllegalArgumentException`() {
        GpsCoordinate(latitude = -91.0, longitude = 0.0, accuracyMeters = 10f, timestampMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `longitude 181 leve IllegalArgumentException`() {
        GpsCoordinate(latitude = 0.0, longitude = 181.0, accuracyMeters = 10f, timestampMs = 0L)
    }

    @Test
    fun `equality reflexive`() {
        val c = GpsCoordinate(36.7538, 3.0588, 50f, 1000L)
        assertEquals(c, c)
    }

    @Test
    fun `deux instances identiques sont egales`() {
        val c1 = GpsCoordinate(36.7538, 3.0588, 50f, 1000L)
        val c2 = GpsCoordinate(36.7538, 3.0588, 50f, 1000L)
        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
    }

    @Test
    fun `instances differentes ne sont pas egales`() {
        val c1 = GpsCoordinate(36.7538, 3.0588, 50f, 1000L)
        val c2 = GpsCoordinate(35.6969, -0.6331, 50f, 1000L)
        assertNotEquals(c1, c2)
    }

    @Test
    fun `copy produit une instance egale`() {
        val original = GpsCoordinate(36.7538, 3.0588, 50f, 1000L)
        val copy = original.copy(accuracyMeters = 100f)
        assertNotEquals(original, copy)
        assertEquals(original.latitude, copy.latitude, 0.0)
        assertEquals(original.longitude, copy.longitude, 0.0)
    }
}
