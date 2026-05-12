package com.mobicloud.domain.util

import com.mobicloud.domain.models.GpsCoordinate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Haversine {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: GpsCoordinate, b: GpsCoordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)

        val sinHalfLat = sin(dLat / 2)
        val sinHalfLng = sin(dLng / 2)
        val h = (sinHalfLat * sinHalfLat + cos(lat1) * cos(lat2) * sinHalfLng * sinHalfLng).coerceIn(0.0, 1.0)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }
}
