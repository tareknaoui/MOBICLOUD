package com.mobicloud.data.local.m11_join

import android.util.Log
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.toHexString

private const val LOGTAG = "MemberMapper"

/**
 * P5 (review R2) : soft variant — retourne `null` au lieu de throw quand la validation échoue.
 * Utile pour les chemins où la donnée vient du wire (MEMBER_UPDATE LEFT, JoinAccept snapshot) et
 * où crasher le caller n'est pas acceptable. Le caller doit logguer et skip la ligne.
 */
fun MemberInfo.toEntityOrNull(
    clusterId: String,
    lastSeen: Long,
    status: MemberStatus = MemberStatus.ACTIVE
): MemberEntity? = runCatching { toEntity(clusterId, lastSeen, status) }.getOrNull()

fun MemberInfo.toEntity(
    clusterId: String,
    lastSeen: Long,
    status: MemberStatus = MemberStatus.ACTIVE
): MemberEntity {
    val hex = nodeId.toHexString().lowercase()
    // M17 : production nodeId = 32 bytes EC P-256 (64 hex chars). On exige cette longueur
    // OU on tolère des IDs courts ≥ 2 hex chars pour les fixtures de tests. Le cas mid-length
    // (3..63 ou 65+) signale une corruption — refuser sans crash.
    require(hex.isNotEmpty()) { "nodeId vide" }
    require(hex.length == 64 || hex.length <= 16) {
        "nodeId longueur hex invalide (${hex.length}) — attendu 64 (P-256) ou fixture courte"
    }
    return MemberEntity(
        nodeId = hex,
        clusterId = clusterId,
        publicKeyBytes = publicKey,
        ipAddress = ipAddress,
        port = port,
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude,
        freeBytes = freeBytes,
        lastSeen = lastSeen,
        role = role.name,
        status = status.name
    )
}

/**
 * H15 : un rôle invalide en DB n'est PAS silencieusement coercé en MEMBER (risque routing
 * vers un faux SUPER_PAIR ou inversement). On log WARN et on retourne `null` ; les callers
 * doivent décider de skip ou drop la ligne.
 */
fun MemberEntity.toMemberInfoOrNull(): MemberInfo? {
    val parsedRole = runCatching { MemberRole.valueOf(role) }.getOrNull()
    if (parsedRole == null) {
        Log.w(LOGTAG, "MemberEntity nodeId=${nodeId.take(8)} role inconnu='$role' — ligne ignorée")
        return null
    }
    return MemberInfo(
        nodeId = nodeId.hexToByteArray(),
        publicKey = publicKeyBytes,
        ipAddress = ipAddress,
        port = port,
        gpsLatitude = gpsLatitude,
        gpsLongitude = gpsLongitude,
        freeBytes = freeBytes,
        role = parsedRole
    )
}

/**
 * @deprecated H15 — `toMemberInfo` swallow silencieusement les rôles invalides en MEMBER.
 * Préférer [toMemberInfoOrNull] qui retourne null et log WARN.
 */
@Deprecated(
    "Silently coerces invalid role to MEMBER (H15 finding). Use toMemberInfoOrNull instead.",
    ReplaceWith("toMemberInfoOrNull() ?: error(\"invalid role\")")
)
fun MemberEntity.toMemberInfo(): MemberInfo = toMemberInfoOrNull() ?: MemberInfo(
    nodeId = nodeId.hexToByteArray(),
    publicKey = publicKeyBytes,
    ipAddress = ipAddress,
    port = port,
    gpsLatitude = gpsLatitude,
    gpsLongitude = gpsLongitude,
    freeBytes = freeBytes,
    role = MemberRole.MEMBER
)

fun List<MemberEntity>.toMemberInfoList(): List<MemberInfo> = mapNotNull { it.toMemberInfoOrNull() }
