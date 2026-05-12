package com.mobicloud.domain.models.m11_join

import com.mobicloud.domain.models.RelayPeer

fun RelayPeer.toSuperPeerHint(): SuperPeerHint = SuperPeerHint(
    nodeId = nodeId.hexToByteArray(),
    clusterId = clusterId,
    ipAddress = ip,
    port = port,
    reliabilityScore = reliabilityScore,
    currentMemberCount = currentMemberCount
)

// Hex string → ByteArray (chaque paire de caractères = 1 octet).
// Rejet strict des longueurs impaires : silencieusement préfixer par '0' produit
// un nodeId différent de celui du pair distant et casse les comparaisons d'égalité.
fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length (got $length)" }
    return ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

fun ByteArray.toHexShort(): String = toHexString().take(8)
