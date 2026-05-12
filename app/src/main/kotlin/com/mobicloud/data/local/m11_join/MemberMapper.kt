package com.mobicloud.data.local.m11_join

import android.util.Log
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.toHexString

private const val LOGTAG = "MemberMapper"

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
        freeBytes = freeBytes,
        lastSeen = lastSeen,
        role = role.name,
        status = status.name
    )
}

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
        freeBytes = freeBytes,
        role = parsedRole
    )
}

@Deprecated(
    "Silently coerces invalid role to MEMBER (H15 finding). Use toMemberInfoOrNull instead.",
    ReplaceWith("toMemberInfoOrNull() ?: error(\"invalid role\")")
)
fun MemberEntity.toMemberInfo(): MemberInfo = toMemberInfoOrNull() ?: MemberInfo(
    nodeId = nodeId.hexToByteArray(),
    publicKey = publicKeyBytes,
    ipAddress = ipAddress,
    port = port,
    freeBytes = freeBytes,
    role = MemberRole.MEMBER
)

fun List<MemberEntity>.toMemberInfoList(): List<MemberInfo> = mapNotNull { it.toMemberInfoOrNull() }
