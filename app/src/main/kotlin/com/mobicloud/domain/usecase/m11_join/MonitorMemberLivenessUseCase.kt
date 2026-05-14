package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.m11_join.LIVENESS_CHECK_INTERVAL_MS
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.models.m11_join.SP_TIMEOUT_MS
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.memberUpdateSignedBytes
import com.mobicloud.domain.models.m11_join.toHexShort
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorMemberLivenessUseCase @Inject constructor(
    private val memberDao: MemberDao,
    private val nodeSettingsRepository: NodeSettingsRepository,
    private val sendMemberUpdateUseCase: SendMemberUpdateUseCase,
    private val networkEventRepository: NetworkEventRepository,
    private val memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase,
    @ApplicationScope private val scope: CoroutineScope,
    val clock: () -> Long = { System.currentTimeMillis() }
) {
    internal var monitorJob: Job? = null

    fun start(spMemberInfo: MemberInfo) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            // M8 : clusterId lu une seule fois au démarrage (V5 mono-cluster par device — il
            // ne change pas durant un mandat SP). Évite un coût `Flow.first()` × 120 cycles/heure.
            val clusterId = runCatching {
                nodeSettingsRepository.observeSettings().first().clusterId
            }.getOrNull().orEmpty()
            if (clusterId.isBlank()) {
                networkEventRepository.pushEvent("[HB-SP-MON] WARN clusterId vide au start — monitor inactif")
                return@launch
            }
            // Keepalive SP→membres : ré-émet le MemberInfo du SP toutes les SP_TIMEOUT_MS/2 (45s)
            // pour que les membres appellent markSpSeen() et ne déclenchent pas BullySolo solo
            // faute de signal SP. Sans ça, le SP ne répond qu'aux JOINED/LEFT — silence 90s → Bully.
            launch {
                while (isActive) {
                    delay(SP_TIMEOUT_MS / 2L)
                    val keepalive = MemberUpdate(
                        event = MemberUpdateEvent.JOINED,
                        member = spMemberInfo,
                        leftNodeId = byteArrayOf(),
                        timestampMs = clock(),
                        signatureBytes = byteArrayOf()
                    )
                    runCatching { sendMemberUpdateUseCase.invoke(keepalive) }
                        .onFailure {
                            networkEventRepository.pushEvent("[HB-SP-MON] WARN keepalive broadcast échoué: ${it.message}")
                        }
                }
            }
            while (isActive) {
                delay(LIVENESS_CHECK_INTERVAL_MS)
                val cutoff = clock() - SP_TIMEOUT_MS
                val deadMembers = memberDao.listActiveSnapshot(clusterId).filter { it.lastSeen < cutoff }
                deadMembers.forEach { dead ->
                    // H12 : protéger la coroutine contre une ligne corrompue (hex invalide, etc.) —
                    // sinon une seule row malformée tue le monitoring du cluster entier.
                    runCatching {
                        val ageMs = clock() - dead.lastSeen
                        networkEventRepository.pushEvent(
                            "[HB-SP-MON] Eviction ${dead.nodeId.take(8)} (silent ${ageMs}ms > 90s)"
                        )
                        // M19 : markEvictedIfStale (compare-and-swap) au lieu de delete brutal —
                        // si un HB est arrivé dans le tick courant, on n'évince pas un membre vivant.
                        // EVICTED rows purgés ensuite via purgeStale (TTL 1h, AC2 spec).
                        val evicted = memberDao.markEvictedIfStale(dead.nodeId, cutoff)
                        if (evicted == 1) {
                            val leftUpdate = MemberUpdate(
                                event = MemberUpdateEvent.LEFT,
                                member = null,
                                leftNodeId = dead.nodeId.hexToByteArray(),
                                timestampMs = clock(),
                                signatureBytes = byteArrayOf()
                            )
                            sendMemberUpdateUseCase.invoke(leftUpdate)
                            // Sync inMemory pour que NetworkViewModel reflète l'éviction côté SP.
                            runCatching { memberSnapshotCacheUseCase.applyUpdate(leftUpdate) }
                        }
                    }.onFailure {
                        networkEventRepository.pushEvent(
                            "[HB-SP-MON] WARN éviction ${dead.nodeId.take(8)} échouée: ${it.message}"
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
