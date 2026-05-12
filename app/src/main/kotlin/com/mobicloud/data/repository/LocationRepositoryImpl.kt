package com.mobicloud.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.mobicloud.domain.models.GpsCoordinate
import com.mobicloud.domain.repository.LocationRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocationRepo"

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val fusedClient: FusedLocationProviderClient,
    private val networkEventRepository: NetworkEventRepository,
    @ApplicationContext private val context: Context
) : LocationRepository {

    private val _currentLocation = MutableStateFlow<GpsCoordinate?>(null)
    override val currentLocation: StateFlow<GpsCoordinate?> = _currentLocation.asStateFlow()

    @Volatile private var started = false

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        TimeUnit.MINUTES.toMillis(5)
    )
        .setMinUpdateDistanceMeters(100f)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationAvailability(availability: com.google.android.gms.location.LocationAvailability) {
            // R1 : ne pas setter started=false ici — FusedLocation auto-reprend quand le signal
            // revient (onLocationAvailability=true), et stop() deviendrait un no-op sinon.
            // Uniquement vider le cache et logger ; la reprise est transparente via le callback.
            if (!availability.isLocationAvailable) {
                _currentLocation.value = null
                networkEventRepository.pushEvent("[GPS] Permission révoquée — admission cluster basée sur capacité seule")
            }
        }

        override fun onLocationResult(result: LocationResult) {
            if (!started) return
            val loc = result.lastLocation ?: return
            val coord = GpsCoordinate(
                latitude      = loc.latitude,
                longitude     = loc.longitude,
                accuracyMeters = loc.accuracy,
                timestampMs   = loc.time
            )
            val isFirst = _currentLocation.value == null
            _currentLocation.value = coord
            if (isFirst) {
                networkEventRepository.pushEvent(
                    "[GPS] Fix acquis lat=${"%.4f".format(coord.latitude)}, " +
                    "lng=${"%.4f".format(coord.longitude)}, accuracy=${coord.accuracyMeters.toInt()}m"
                )
            }
        }
    }

    override fun start() {
        if (started) return
        started = true

        if (!hasPermission()) {
            started = false
            networkEventRepository.pushEvent("[GPS] Permission refusée — admission cluster basée sur capacité seule")
            return
        }
        networkEventRepository.pushEvent("[GPS] LocationProvider démarré (priority=BALANCED_POWER, interval=5min)")
        requestUpdates()
    }

    private fun requestUpdates() {
        if (!hasPermission()) {
            _currentLocation.value = null
            networkEventRepository.pushEvent("[GPS] Permission révoquée — admission cluster basée sur capacité seule")
            started = false
            return
        }
        try {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // R2 : réinitialiser started pour que start() puisse être retentée (ex. START_STICKY).
            started = false
            Log.i(TAG, "GPS indisponible — admission cluster basée sur capacité seule")
            networkEventRepository.pushEvent("[GPS] Permission révoquée — admission cluster basée sur capacité seule")
            _currentLocation.value = null
        } catch (e: ApiException) {
            started = false
            Log.i(TAG, "GPS indisponible — admission cluster basée sur capacité seule")
            _currentLocation.value = null
        } catch (e: Exception) {
            started = false
            Log.i(TAG, "GPS indisponible — admission cluster basée sur capacité seule")
            _currentLocation.value = null
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        // La dernière valeur en cache est conservée intentionnellement :
        // un GPS lent indoor doit pouvoir réutiliser la dernière position
        // connue tant que l'utilisateur ne s'est pas déplacé. La fraîcheur
        // reste consultable via GpsCoordinate.timestampMs.
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w(TAG, "removeLocationUpdates échoué", e)
        }
        networkEventRepository.pushEvent("[GPS] LocationProvider arrêté")
    }

    private fun hasPermission(): Boolean =
        // Appel direct via Context.checkSelfPermission (API 23+, minSdk=24 OK).
        // Évite ContextCompat qui traverse TextUtils.equals → RuntimeException en JVM tests
        // (Android framework non mocké). Évite aussi Process.myPid()/myUid() pour la même raison.
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
