package com.mobicloud.domain.repository

import com.mobicloud.domain.models.GpsCoordinate
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    /**
     * Position GPS courante. `null` si :
     *  - permission ACCESS_FINE_LOCATION refusée
     *  - fix GPS indisponible (indoor, cold start, désactivé)
     *  - cache RAM expiré sans nouveau fix
     * Émet la dernière valeur en cache (StateFlow, replay=1) — NFR-10.
     */
    val currentLocation: StateFlow<GpsCoordinate?>

    /** Démarre les updates (idempotent ; appelé par NetworkForegroundService). */
    fun start()

    /** Arrête les updates et libère le callback FusedLocationProvider. */
    fun stop()
}
