package com.mobicloud.data.local.m11_join

import android.util.Log
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.toHexString

private const val LOGTAG = "MemberMapper"

fun MemberInfo.toEntity(
    clusterId: String,
    lastSeen: Long,
    status: MemberStatus = MemberStatus.ACTIVE
): MemberEntity {
    val hex = nodeId.toHexString().lowercase()
    // Note (M17) : production nodeId = 32 bytes EC P-256 (64 hex chars). On ne require
    // pas la longueur ici car les fixtures de tests utilisent des IDs courts ; la
    // validation forte est à la limite du protocole (signing payload), pas du mapper.
    require(hex.isNotEmpty()) { "nodeId vide" }
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
