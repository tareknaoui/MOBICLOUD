package com.mobicloud.data.repository

import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import com.mobicloud.domain.usecase.m01_discovery.ITrustScoreProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val UPTIME_MAX_MS = 86_400_000L  // 24 heures → score uptime = 1.0

class ReliabilityScoreProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ITrustScoreProvider {

    override suspend fun getScore(): Float = withContext(Dispatchers.Default) {
        val battery = getBatteryLevel()
        val uptime = getUptimeScore()
        val network = getNetworkScore()
        (battery * 0.4f + uptime * 0.4f + network * 0.2f).coerceIn(0.0f, 1.0f)
    }

    private fun getBatteryLevel(): Float {
        val intent = context.registerReceiver(null, IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level.toFloat() / scale.toFloat() else 0.5f
    }

    private fun getUptimeScore(): Float {
        val uptimeMs = SystemClock.elapsedRealtime()
        return (uptimeMs.toFloat() / UPTIME_MAX_MS).coerceIn(0.0f, 1.0f)
    }

    private fun getNetworkScore(): Float {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return 0.3f
        return when {
            // WiFi validé uniquement (portail captif = 0.3, inutilisable pour du P2P)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> 1.0f
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 0.7f
            else -> 0.3f
        }
    }
}
