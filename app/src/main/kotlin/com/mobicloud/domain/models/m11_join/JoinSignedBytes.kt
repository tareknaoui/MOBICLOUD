package com.mobicloud.domain.models.m11_join

import java.security.MessageDigest

// Helpers top-level pour la construction des bytes signés JOIN.
// Format versionné (v1|...) cohérent avec electionSignedBytes.
// timestampMs inclus dans chaque signature pour empêcher le replay.

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Hash canonique d'un snapshot de membres. Trié par nodeId pour être ordering-indépendant.
 * Le candidat re-calcule ce hash sur le snapshot reçu et vérifie qu'il matche les bytes signés ;
 * un MITM ne peut donc pas altérer la liste sans casser la signature.
 */
fun memberSnapshotHash(snapshot: List<MemberInfo>): String {
    val canonical = snapshot
        .sortedBy { it.nodeId.toHex() }
        .joinToString("|") { "${it.nodeId.toHex()},${it.role.name},${it.freeBytes}" }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.toHex()
}

fun joinRequestSignedBytes(
    senderNodeId: ByteArray,
    candidatePublicKey: ByteArray,
    gpsLatitude: Double?,
    gpsLongitude: Double?,
    freeBytes: Long,
    reliabilityScore: Float,
    timestampMs: Long
): ByteArray = buildString {
    append("v1|JOIN_REQUEST|")
    append(senderNodeId.toHex())
    append("|")
    append(candidatePublicKey.toHex())
    append("|")
    append(gpsLatitude ?: "null")
    append("|")
    append(gpsLongitude ?: "null")
    append("|")
    append(freeBytes)
    append("|")
    append(reliabilityScore.toRawBits())
    append("|")
    append(timestampMs)
}.toByteArray(Charsets.UTF_8)

fun joinAcceptSignedBytes(
    clusterId: String,
    superPairNodeId: ByteArray,
    timestampMs: Long,
    memberSnapshot: List<MemberInfo>
): ByteArray = buildString {
    append("v1|JOIN_ACCEPT|")
    append(clusterId)
    append("|")
    append(superPairNodeId.toHex())
    append("|")
    append(timestampMs)
    append("|")
    // Inclure le hash du snapshot membres pour qu'un MITM ne puisse pas altérer la liste
    // après émission (décision review Story 11.2 — Hybride).
    append(memberSnapshotHash(memberSnapshot))
}.toByteArray(Charsets.UTF_8)

fun joinRedirectSignedBytes(
    reason: JoinRedirectReason,
    timestampMs: Long
): ByteArray = buildString {
    append("v1|JOIN_REDIRECT|")
    append(reason.name)
    append("|")
    append(timestampMs)
}.toByteArray(Charsets.UTF_8)
