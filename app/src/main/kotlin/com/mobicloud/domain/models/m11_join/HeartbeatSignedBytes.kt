package com.mobicloud.domain.models.m11_join

/**
 * Format des payloads cryptographiquement signés Story 11.3.
 *
 * Décisions wire format V5 (review 2026-05-12) :
 * - `v1|` reste le préfixe protocole (versionning sémantique manuel).
 * - Tous les hex sont en minuscules (`lowercase`) pour éviter les mismatchs verify.
 * - `ipAddress` est validé en amont via [requireSafeIpAddress] : rejet de `|` et `:`
 *   non-IPv6 ; IPv6 ne peut pas être encodé inline tant qu'on n'a pas migré au bracketing.
 *   En V5 cluster mono-WiFi LAN, IPv4 suffit ; IPv6 rejeté → caller doit valider.
 * - HEARTBEAT contient `ip:port` car le SP a besoin de l'endpoint (legacy).
 * - MEMBER_UPDATE inclut désormais le `senderNodeId` (SP émetteur) pour défense crypto AC14 :
 *   le receveur peut prouver crypto-bound que c'est bien le SP courant qui a signé.
 * - LEAVE inclut désormais le `clusterId` pour empêcher le replay cross-cluster.
 */

/** Valide un ipAddress pour inclusion dans un payload signé. Rejette `|` et IPv6 inline. */
fun requireSafeIpAddress(ip: String): String {
    require(ip.isNotBlank()) { "ipAddress vide" }
    require('|' !in ip) { "ipAddress contient '|' (collision séparateur signé)" }
    // IPv6 inline non supporté en V5 (collision avec `ip:port`). Détection : présence de `::`
    // ou plus d'un `:` non-port. IPv4 valide a 0 `:`. Tout `:` rejeté côté payload-input.
    require(':' !in ip) { "ipAddress IPv6 ou contient ':' — V5 IPv4-only (bracketing prévu V5.1)" }
    return ip
}

fun heartbeatSignedBytes(
    nodeId: ByteArray,
    freeBytes: Long,
    ip: String,
    port: Int,
    ts: Long
): ByteArray {
    val nodeIdHex = nodeId.toHexString().lowercase()
    val safeIp = requireSafeIpAddress(ip)
    return "v1|HEARTBEAT|$nodeIdHex|$freeBytes|$safeIp:$port|$ts".toByteArray(Charsets.UTF_8)
}

/**
 * Payload signé d'un MEMBER_UPDATE. Inclut le `senderNodeId` (le SP émetteur) pour
 * fermer la défense AC14 au niveau crypto (pas seulement transport).
 */
fun memberUpdateSignedBytes(
    senderNodeId: ByteArray,
    event: MemberUpdateEvent,
    memberOrNodeIdHex: String,
    ts: Long
): ByteArray {
    val senderHex = senderNodeId.toHexString().lowercase()
    val targetHex = memberOrNodeIdHex.lowercase()
    return "v1|MEMBER_UPDATE|$senderHex|${event.name}|$targetHex|$ts".toByteArray(Charsets.UTF_8)
}

/**
 * Payload signé d'un LEAVE. Inclut le `clusterId` pour empêcher le replay cross-cluster
 * d'une même identité dans un autre contexte.
 */
fun leaveSignedBytes(
    nodeId: ByteArray,
    clusterId: String,
    ts: Long
): ByteArray {
    val nodeIdHex = nodeId.toHexString().lowercase()
    return "v1|LEAVE|$clusterId|$nodeIdHex|$ts".toByteArray(Charsets.UTF_8)
}
