package com.mobicloud.domain.util

/**
 * Rejette les IPs non-routables en littéral (sans DNS lookup) : loopback, any-local,
 * link-local, multicast. Utilisé en garde défensive sur les directives de migration/
 * réplication signées : un SP compromis pourrait y placer ces adresses pour DoS / leak.
 */
internal fun isRoutableIpLiteral(ip: String): Boolean {
    if (ip.isBlank()) return false
    if (ip == "0.0.0.0" || ip == "::" || ip == "255.255.255.255") return false
    if (ip.startsWith("127.")) return false
    if (ip.startsWith("169.254.")) return false
    if (ip.startsWith("224.") || ip.startsWith("239.")) return false
    val lower = ip.lowercase()
    if (lower == "::1" || lower.startsWith("fe80:") || lower.startsWith("ff00:")) return false
    return true
}
