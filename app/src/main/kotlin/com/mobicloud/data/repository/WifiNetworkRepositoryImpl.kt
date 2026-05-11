package com.mobicloud.data.repository

import android.content.Context
import android.net.wifi.WifiManager
import com.mobicloud.domain.repository.WifiNetworkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
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

    // FIX GOSSIP TCP : parcours des interfaces reseau pour retrouver une IPv4 LAN
    // joignable. Couvre WiFi client (wlan0), hotspot AP (ap0/wlan0) et ethernet.
    // Exclut loopback (127.x), link-local (169.254.x), et IPv6.
    override fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
