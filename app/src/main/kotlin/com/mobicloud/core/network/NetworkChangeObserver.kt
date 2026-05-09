package com.mobicloud.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.mobicloud.domain.usecase.m06_m07_repair_migration.SendDepartureNoticeUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sendDepartureNoticeUseCase: SendDepartureNoticeUseCase
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Fix P3 : register() idempotente — évite double-registration sur START_STICKY restart
    private val isRegistered = AtomicBoolean(false)
    // Fix P1 : un seul DEPARTURE_NOTICE à la fois, scope annulable dans unregister()
    private val isDeparturePending = AtomicBoolean(false)
    @Volatile private var departureScope: CoroutineScope? = null

    /**
     * Hook optionnel declenche a chaque WiFi disponible. Utilise par le service P2P
     * pour rafraichir le clusterId via refreshClusterIdFromWifi() -- couvre le cas
     * ou l'app a demarre avant d'etre connectee au WiFi (clusterId reste vide
     * jusqu'au callback) ou quand l'utilisateur change de reseau.
     */
    @Volatile var onWifiAvailable: (() -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // SSID disponible (avec permission FINE_LOCATION + service localisation actif).
            // Le hook est best-effort -- toute exception cote consumer est avalee
            // pour ne pas casser le NetworkCallback systeme.
            runCatching { onWifiAvailable?.invoke() }
        }

        override fun onLost(network: Network) {
            // Fix P2 : NetworkRequest.addTransportType(TRANSPORT_WIFI) garantit déjà
            // que ce callback ne se déclenche que pour la perte du WiFi —
            // getNetworkCapabilities() sur un réseau perdu retourne null sur API 26+,
            // le double-check était donc un faux garde.
            if (isDeparturePending.compareAndSet(false, true)) {
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                departureScope = scope
                scope.launch {
                    try {
                        sendDepartureNoticeUseCase()
                    } finally {
                        isDeparturePending.set(false)
                    }
                }
            }
        }
    }

    fun register() {
        if (isRegistered.compareAndSet(false, true)) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    fun unregister() {
        if (isRegistered.compareAndSet(true, false)) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
        departureScope?.cancel()
        departureScope = null
        isDeparturePending.set(false)
    }
}
