package com.mobicloud.data.repository

import com.mobicloud.domain.models.GpsCoordinate
import com.mobicloud.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockLocationRepositoryImpl @Inject constructor() : LocationRepository {

    private val _currentLocation = MutableStateFlow<GpsCoordinate?>(null)
    override val currentLocation: StateFlow<GpsCoordinate?> = _currentLocation.asStateFlow()

    override fun start() { /* no-op — position contrôlée par setMockLocation() */ }
    override fun stop()  { /* no-op */ }

    /** Injecte une position GPS simulée (null = GPS indisponible). */
    fun setMockLocation(coord: GpsCoordinate?) {
        _currentLocation.value = coord
    }
}
