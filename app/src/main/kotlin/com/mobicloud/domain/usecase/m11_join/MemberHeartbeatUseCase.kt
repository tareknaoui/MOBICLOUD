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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
    companion object {
        // P10 (review R2) : `const val` en lieu et place d'un `val` instance — naming convention
        // Kotlin pour les vraies constantes.
        private const val LOCAL_ERROR_GRACE_MS: Long = HEARTBEAT_INTERVAL_MS * 2L  // 60s
    }

    internal var heartbeatJob: Job? = null
    private val lifecycleMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile
    private var lastSpSignalAt: Long = 0L

    // H13 : on isole les échecs locaux (signData transient, ipAddress invalide) du silence SP.
    // Quand un échec local survient juste avant `checkSpTimeout`, on ne déclenche pas un Bully
    // injuste sur l'état du SP — c'est nous qui n'avons pas pu envoyer.
    @Volatile
    private var lastLocalSendErrorAt: Long = 0L

    // P1 (review R2) : `start()` devient `suspend` + `cancelAndJoin()` du Job précédent dans le
    // mutex. L'ancienne version wrappait dans `scope.launch { mutex.withLock { ... } }` ce qui
    // réintroduisait la race M11 : deux `start()` consécutifs pouvaient être schedulés AVANT que
    // l'un n'acquière le mutex, créant des Jobs orphelins. Pattern `cancelAndJoin` garantit que
    // l'ancien cycle est mort avant qu'on en démarre un nouveau.
    suspend fun start(superPairNodeId: ByteArray) {
        lifecycleMutex.withLock {
            heartbeatJob?.cancelAndJoin()
            lastSpSignalAt = clock()
            lastLocalSendErrorAt = 0L
            heartbeatJob = scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    sendOnce(superPairNodeId)
                    checkSpTimeout(superPairNodeId)
                }
            }
        }
    }

    private suspend fun sendOnce(spNodeId: ByteArray) {
        val identity = identityRepository.getIdentity().getOrElse {
            lastLocalSendErrorAt = clock()
            return
        }
        val freeBytes = nodeSettingsRepository.observeFreeSpaceBytes().first()
        val ip = wifiNetworkRepository.getLocalIpAddress() ?: "0.0.0.0"
        val port = 0
        val ts = clock()
        // heartbeatSignedBytes rejette IPv6 / pipe via requireSafeIpAddress.
        // En V5 IPv4-only ; si le wifi retourne autre chose, on skip ce cycle proprement.
        val signedBytes = runCatching {
            heartbeatSignedBytes(identity.nodeId.hexToByteArray(), freeBytes, ip, port, ts)
        }.getOrElse {
            lastLocalSendErrorAt = clock()
            networkEventRepository.pushEvent("[HB-MEM] WARN ipAddress invalide ('$ip') — cycle skip")
            return
        }
        val signature = securityRepository.signData(signedBytes).getOrElse {
            lastLocalSendErrorAt = clock()
            networkEventRepository.pushEvent("[HB-MEM] WARN signData échoué — cycle skip")
            return
        }
        val hb = Heartbeat(identity.nodeId.hexToByteArray(), freeBytes, ip, port, ts, signature)
        memberHeartbeatSender.send(spNodeId, hb)
            .onSuccess {
                // P3 (review R2) : reset du compteur d'erreurs locales sur envoi réussi. Sans ça,
                // une transient pré-ancienne suspendait indéfiniment la détection SP timeout
                // (lastLocalSendErrorAt jamais effacé → grace period permanente → faux silence SP).
                lastLocalSendErrorAt = 0L
                networkEventRepository.pushEvent(
                    "[HB-MEM] Heartbeat envoyé → ${spNodeId.toHexShort()} ts=$ts"
                )
            }
            .onFailure {
                lastLocalSendErrorAt = clock()
                networkEventRepository.pushEvent("[HB-MEM] WARN envoi heartbeat échoué: ${it.message}")
            }
        // markSpSeen() N'est PAS appelé ici — seulement via réception MEMBER_UPDATE (AC11)
    }

    private suspend fun checkSpTimeout(spNodeId: ByteArray) {
        val now = clock()
        // H13 : on n'ouvre Bully que si silence SP confirmé ET pas d'erreur locale récente.
        // Un orage de signData/network sur le device le ferait basculer faussement en Bully.
        val spSilent = now - lastSpSignalAt > SP_TIMEOUT_MS
        val localOk = now - lastLocalSendErrorAt > LOCAL_ERROR_GRACE_MS
        if (spSilent && localOk) {
            networkEventRepository.pushEvent(
                "[HB-MEM] ERROR SP timeout — déclenche Bully"
            )
            joinStateMachine.transition(JoinEvent.SpTimeoutDetected(spNodeId))
            stop()
        } else if (spSilent && !localOk) {
            networkEventRepository.pushEvent(
                "[HB-MEM] SP timeout suspecté mais erreurs locales récentes — grace period"
            )
        }
    }

    /** Appelé par le service quand un signal du SP est reçu (MEMBER_UPDATE ou ACK futur). */
    fun markSpSeen() {
        lastSpSignalAt = clock()
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        // P7 (review R2) : reset des compteurs au stop pour éviter un faux Bully immédiat sur un
        // futur start() après un cycle SuperPair↔Member (où lastSpSignalAt restait à 0L initial
        // et `now - 0 > SP_TIMEOUT_MS` était vrai instantanément).
        lastSpSignalAt = 0L
        lastLocalSendErrorAt = 0L
    }
}
