package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.domain.models.BULLY_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.heartbeatSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject

class UnknownMemberException(nodeId: String) : Exception("Heartbeat de nodeId inconnu: $nodeId")
class StaleTimestampException(ts: Long, now: Long) : Exception("Timestamp hors fenêtre: $ts (now=$now)")

class ProcessHeartbeatUseCase @Inject constructor(
    private val memberDao: MemberDao,
    private val securityRepository: SecurityRepository,
    private val joinStateMachine: JoinStateMachine,
    private val networkEventRepository: NetworkEventRepository
) {
    suspend operator fun invoke(hb: Heartbeat): Result<Unit> {
        val state = joinStateMachine.currentState.value
        if (state !is NodeJoinState.SuperPair) {
            networkEventRepository.pushEvent(
                "[HB-SP] Heartbeat reçu hors état SuperPair, ignoré (state=${state::class.simpleName})"
            )
            return Result.success(Unit)
        }

        val nodeIdHex = hb.senderNodeId.toHexString()
        val nodeIdShort = hb.senderNodeId.toHexShort()

        val memberEntity = memberDao.findByNodeId(nodeIdHex)
        if (memberEntity == null) {
            networkEventRepository.pushEvent(
                "[HB-SP] Heartbeat d'un nodeId inconnu $nodeIdShort (jamais JOINé) — ignoré"
            )
            return Result.failure(UnknownMemberException(nodeIdHex))
        }

        // requireSafeIpAddress rejette `|` (collision séparateur) et IPv6 inline (collision ip:port).
        // Si l'attaquant a injecté du bruit dans hb.ipAddress, on rejette AVANT signature verify.
        val signedBytes = runCatching {
            heartbeatSignedBytes(hb.senderNodeId, hb.freeBytes, hb.ipAddress, hb.port, hb.timestampMs)
        }.getOrElse {
            networkEventRepository.pushEvent(
                "[HB-SP] Heartbeat ipAddress invalide ('${hb.ipAddress}') de $nodeIdShort — rejeté"
            )
            return Result.failure(it)
        }
        val valid = securityRepository.verifySignature(signedBytes, hb.signatureBytes, memberEntity.publicKeyBytes)
            .getOrElse { return Result.failure(it) }
        if (!valid) {
            networkEventRepository.pushEvent("[HB-SP] Heartbeat invalide (signature) de $nodeIdShort")
            return Result.failure(Exception("Signature invalide"))
        }

        val now = System.currentTimeMillis()
        // (now - W) et (now + W) ne peuvent pas overflow (now ~ 10^13, W = 30s).
        // Comparer ts contre des bornes pré-calculées évite l'overflow de `abs(Long.MIN_VALUE)`.
        if (hb.timestampMs < now - BULLY_TIMESTAMP_WINDOW_MS || hb.timestampMs > now + BULLY_TIMESTAMP_WINDOW_MS) {
            networkEventRepository.pushEvent(
                "[HB-SP] Heartbeat invalide (timestamp stale ts=${hb.timestampMs} now=$now) de $nodeIdShort"
            )
            return Result.failure(StaleTimestampException(hb.timestampMs, now))
        }

        // port=0 = convention relay-bound (architecture sans connexion directe). 1..65535 trop strict.
        if (hb.ipAddress.isBlank() || hb.port !in 0..65535) {
            networkEventRepository.pushEvent(
                "[HB-SP] Heartbeat invalide (ip/port invalide) de $nodeIdShort"
            )
            return Result.success(Unit)
        }

        val updated = memberDao.touchHeartbeat(
            nodeId = nodeIdHex,
            lastSeen = now,
            freeBytes = hb.freeBytes,
            ip = hb.ipAddress,
            port = hb.port
        )
        if (updated != 1) {
            networkEventRepository.pushEvent(
                "[HB-SP] WARN touchHeartbeat retourné $updated pour $nodeIdShort (race condition acceptable)"
            )
        }

        networkEventRepository.pushEvent(
            "[HB-SP] Heartbeat OK $nodeIdShort freeBytes=${hb.freeBytes} ip=${hb.ipAddress}:${hb.port}"
        )
        return Result.success(Unit)
    }
}
