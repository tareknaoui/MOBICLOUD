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
import com.mobicloud.domain.models.m11_join.toHexString
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
    // Ghost-member fix : nodeIdHex → timestamp d'éviction. Répétés comme LEFT dans le keepalive
    // pendant SP_TIMEOUT_MS × 3 (4.5 min = 6 cycles) pour atteindre les pairs qui ont raté
    // le LEFT initial (blip relay transitoire).
    private val recentlyEvicted = java.util.concurrent.ConcurrentHashMap<String, Long>()

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
            // Keepalive SP→membres : ré-émet le MemberInfo de TOUS les membres actifs toutes les
            // SP_TIMEOUT_MS/2 (45s). Cela permet aux pairs qui ont raté un MEMBER_UPDATE (reconnexion
            // relay transitoire) de resynchroniser leur inMemory sans quitter/rejoindre le cluster.
            // S1 (stabilité long-terme) : chaque ITÉRATION wrapée dans runCatching — sans ça, une
            // exception transitoire (DB I/O, NPE) tuait la boucle pour toujours → cluster figé.
            launch {
                while (isActive) {
                    delay(SP_TIMEOUT_MS / 2L)
                    runCatching {
                        val now = clock()
                        val allMembers = memberSnapshotCacheUseCase.snapshot()
                        for (m in allMembers) {
                            val update = MemberUpdate(
                                event = MemberUpdateEvent.JOINED,
                                member = m,
                                leftNodeId = byteArrayOf(),
                                timestampMs = now,
                                signatureBytes = byteArrayOf()
                            )
                            runCatching { sendMemberUpdateUseCase.invoke(update) }
                                .onFailure {
                                    networkEventRepository.pushEvent("[HB-SP-MON] WARN keepalive ${m.nodeId.toHexShort()} échoué: ${it.message}")
                                }
                        }
                        // Ghost-member fix : répéter LEFT pour les membres évincés récemment afin que
                        // les pairs qui ont raté le LEFT initial puissent purger leur inMemory.
                        val evictionTtl = SP_TIMEOUT_MS * 3L
                        recentlyEvicted.entries.removeIf { now - it.value >= evictionTtl }
                        for ((nodeIdHex, _) in recentlyEvicted) {
                            val leftUpdate = MemberUpdate(
                                event = MemberUpdateEvent.LEFT,
                                member = null,
                                leftNodeId = nodeIdHex.hexToByteArray(),
                                timestampMs = clock(),
                                signatureBytes = byteArrayOf()
                            )
                            runCatching { sendMemberUpdateUseCase.invoke(leftUpdate) }
                                .onFailure {
                                    networkEventRepository.pushEvent("[HB-SP-MON] WARN keepalive LEFT ${nodeIdHex.take(8)} échoué: ${it.message}")
                                }
                        }
                    }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        networkEventRepository.pushEvent("[HB-SP-MON] WARN cycle keepalive échoué: ${it.message} — retry next tick")
                    }
                }
            }
            // S1 (stabilité long-terme) : idem pour la boucle d'éviction — l'ancien code laissait
            // `memberDao.listActiveSnapshot()` hors runCatching → 1 exception Room = monitor mort →
            // cluster accumule des ghost members à l'infini.
            while (isActive) {
                delay(LIVENESS_CHECK_INTERVAL_MS)
                runCatching {
                    val cutoff = clock() - SP_TIMEOUT_MS
                    // Rafraîchit lastSeen du SP lui-même — il ne reçoit pas de heartbeat de sa propre part
                    // et serait sinon évincé après SP_TIMEOUT_MS comme n'importe quel membre.
                    runCatching {
                        memberDao.touchHeartbeat(
                            nodeId = spMemberInfo.nodeId.toHexString().lowercase(),
                            lastSeen = clock(),
                            freeBytes = spMemberInfo.freeBytes,
                            ip = spMemberInfo.ipAddress,
                            port = spMemberInfo.port
                        )
                    }
                    val spNodeIdHex = spMemberInfo.nodeId.toHexString().lowercase()
                    val deadMembers = memberDao.listActiveSnapshot(clusterId)
                        .filter { it.lastSeen < cutoff && it.nodeId != spNodeIdHex }
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
                            // Ghost-member fix : mémoriser l'éviction pour la répéter dans le keepalive.
                            recentlyEvicted[dead.nodeId] = clock()
                        }
                    }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        networkEventRepository.pushEvent(
                            "[HB-SP-MON] WARN éviction ${dead.nodeId.take(8)} échouée: ${it.message}"
                        )
                    }
                }
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    networkEventRepository.pushEvent("[HB-SP-MON] WARN cycle éviction échoué: ${it.message} — retry next tick")
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
