package com.mobicloud.data.repository

import android.content.Context
import android.net.wifi.WifiManager
import com.mobicloud.domain.repository.WifiNetworkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WifiNetworkRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WifiNetworkRepository {

    @Suppress("DEPRECATION")
    override fun getCurrentSsid(): String? {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val raw = wm.connectionInfo?.ssid ?: return null
        // Android wraps SSID in quotes — strip them
        val ssid = raw.removePrefix("\"").removeSuffix("\"")
        return ssid.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }
}
