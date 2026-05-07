package com.mobicloud.domain.repository

interface WifiNetworkRepository {
    /** Returns the current WiFi SSID, or null if not connected to WiFi. */
    fun getCurrentSsid(): String?
}
