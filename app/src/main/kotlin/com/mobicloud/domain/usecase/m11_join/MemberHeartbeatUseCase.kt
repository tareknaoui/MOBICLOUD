package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.m11_join.HEARTBEAT_INTERVAL_MS
import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.JoinEvent
import com.mobicloud.domain.models.m11_join.NodeJoinState
import com.mobicloud.domain.models.m11_join.SP_TIMEOUT_MS
import com.mobicloud.domain.models.m11_join.heartbeatSignedBytes
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.repository.WifiNetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberHeartbeatUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val identityRepository: IdentityRepository,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val memberHeartbeatSender: IMemberHeartbeatSender,
    private val wifiNetworkRepository: WifiNetworkRepository,
    private val joinStateMachine: JoinStateMachine,
    private val networkEventRepository: NetworkEventRepository,
    @ApplicationScope private val scope: CoroutineScope,
    val clock: () -> Long = { System.currentTimeMillis() }
) {
    internal var heartbeatJob: Job? = null

    @Volatile
    private var lastSpSignalAt: Long = 0L

    fun start(superPairNodeId: ByteArray) {
        if (heartbeatJob?.isActive == true) return
        lastSpSignalAt = clock()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendOnce(superPairNodeId)
                checkSpTimeout(superPairNodeId)
            }
        }
    }

    private suspend fun sendOnce(spNodeId: ByteArray) {
        val identity = identityRepository.getIdentity().getOrElse { return }
        val freeBytes = nodeSettingsRepository.observeFreeSpaceBytes().first()
        val ip = wifiNetworkRepository.getLocalIpAddress() ?: "0.0.0.0"
        val port = 0
        val ts = clock()
        // heartbeatSignedBytes rejette IPv6 / pipe via requireSafeIpAddress.
        // En V5 IPv4-only ; si le wifi retourne autre chose, on skip ce cycle proprement.
        val signedBytes = runCatching {
            heartbeatSignedBytes(identity.nodeId.hexToByteArray(), freeBytes, ip, port, ts)
        }.getOrElse {
            networkEventRepository.pushEvent("[HB-MEM] WARN ipAddress invalide ('$ip') — cycle skip")
            return
        }
        val signature = securityRepository.signData(signedBytes).getOrElse {
            networkEventRepository.pushEvent("[HB-MEM] WARN signData échoué — cycle skip")
            return
        }
        val hb = Heartbeat(identity.nodeId.hexToByteArray(), freeBytes, ip, port, ts, signature)
        memberHeartbeatSender.send(spNodeId, hb)
            .onSuccess {
                networkEventRepository.pushEvent(
                    "[HB-MEM] Heartbeat envoyé → ${spNodeId.toHexShort()} ts=$ts"
                )
            }
            .onFailure {
                networkEventRepository.pushEvent("[HB-MEM] WARN envoi heartbeat échoué: ${it.message}")
            }
        // markSpSeen() N'est PAS appelé ici — seulement via réception MEMBER_UPDATE (AC11)
    }

    private suspend fun checkSpTimeout(spNodeId: ByteArray) {
        if (clock() - lastSpSignalAt > SP_TIMEOUT_MS) {
            networkEventRepository.pushEvent(
                "[HB-MEM] ERROR SP timeout — déclenche Bully"
            )
            joinStateMachine.transition(JoinEvent.SpTimeoutDetected(spNodeId))
            stop()
        }
    }

    /** Appelé par le service quand un signal du SP est reçu (MEMBER_UPDATE ou ACK futur). */
    fun markSpSeen() {
        lastSpSignalAt = clock()
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
