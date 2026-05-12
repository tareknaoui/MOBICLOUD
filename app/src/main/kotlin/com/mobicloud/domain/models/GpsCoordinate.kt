package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMs: Long
) {
    init {
        require(latitude in -90.0..90.0) { "latitude hors bornes : $latitude" }
        require(longitude in -180.0..180.0) { "longitude hors bornes : $longitude" }
    }
}
