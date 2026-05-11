package com.mobicloud.domain.repository

interface WifiNetworkRepository {
    /** Returns the current WiFi SSID, or null if not connected to WiFi. */
    fun getCurrentSsid(): String?

    /**
     * Retourne l'IP IPv4 LAN du telephone (e.g. 192.168.x.x sur WiFi/hotspot).
     * Utile pour annoncer une adresse joignable par les pairs sur le meme LAN.
     * Retourne null si aucune interface IPv4 non-loopback non-link-local n'est trouvee
     * (cas 4G pur derriere CGNAT, ou pas de reseau).
     */
    fun getLocalIpAddress(): String?
}
