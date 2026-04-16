package com.mobicloud.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import com.mobicloud.domain.models.NetworkType
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.PeerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val peerRepository: PeerRepository,
    private val identityRepository: IdentityRepository
) : DiagnosticsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _diagnostics = MutableStateFlow(NodeDiagnostics.DEFAULT)
    override val diagnostics: StateFlow<NodeDiagnostics> = _diagnostics

    init {
        scope.launch {
            while (isActive) {
                _diagnostics.value = buildDiagnostics()
                delay(5_000L)
            }
        }
    }

    private suspend fun buildDiagnostics(): NodeDiagnostics {
        val battery = getBatteryPercent()
        val uptime = SystemClock.elapsedRealtime()
        val network = getNetworkType()
        val peers = peerRepository.peers.value.count { it.isActive }
        val score = identityRepository.getIdentity().getOrNull()?.reliabilityScore ?: 0f
        return NodeDiagnostics(battery, uptime, network, peers, score)
    }

    private fun getBatteryPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (scale > 0) (level * 100 / scale) else 0
    }

    private fun getNetworkType(): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkType.UNKNOWN
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.UNKNOWN
        }
    }
}
