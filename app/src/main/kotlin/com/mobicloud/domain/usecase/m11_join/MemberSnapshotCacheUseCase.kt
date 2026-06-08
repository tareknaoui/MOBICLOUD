package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberSnapshotDao
import com.mobicloud.data.local.entity.MemberSnapshotEntity
import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NodeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberSnapshotCacheUseCase @Inject constructor(
    private val snapshotDao: MemberSnapshotDao,
    private val nodeSettingsRepository: NodeSettingsRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _inMemory = MutableStateFlow<List<MemberInfo>>(emptyList())
    val inMemory: StateFlow<List<MemberInfo>> = _inMemory.asStateFlow()
    private val updateMutex = Mutex()

    private var _inMemoryClusterId: String = ""

    fun snapshotClusterId(): String = _inMemoryClusterId

    suspend fun seedFromJoinAccept(
        clusterId: String,
        superPairNodeId: ByteArray,
        snapshot: List<MemberInfo>
    ) {
        _inMemoryClusterId = clusterId
        _inMemory.value = snapshot
        snapshotDao.upsert(
            MemberSnapshotEntity(
                clusterId = clusterId,
                superPairNodeIdHex = superPairNodeId.toHexString(),
                lastUpdatedMs = System.currentTimeMillis(),
                membersJson = Json.encodeToString(snapshot)
            )
        )
    }

    suspend fun applyUpdate(update: MemberUpdate) {
        val updated = updateMutex.withLock {
            val current = _inMemory.value
            val next = when (update.event) {
                MemberUpdateEvent.JOINED -> {
                    val m = update.member ?: return@withLock null
                    current.filterNot { it.nodeId.contentEquals(m.nodeId) } + m
                }
                MemberUpdateEvent.LEFT -> {
                    if (update.leftNodeId.isEmpty()) return@withLock null
                    current.filterNot { it.nodeId.contentEquals(update.leftNodeId) }
                }
            }
            _inMemory.value = next
            next
        } ?: return
        scope.launch {
            // Récupère le clusterId courant via NodeSettings (un seul cluster par device V5).
            val clusterId = runCatching {
                nodeSettingsRepository.observeSettings().first().clusterId
            }.getOrNull()
            if (clusterId.isNullOrBlank()) return@launch
            val entity = snapshotDao.get(clusterId) ?: return@launch
            snapshotDao.upsert(
                entity.copy(
                    lastUpdatedMs = System.currentTimeMillis(),
                    membersJson = Json.encodeToString(updated)
                )
            )
        }
    }

    /**
     * [HIGH-1 fix 2026-06-08] Promeut [newSpNodeId] en SUPER_PAIR et rétrograde tout autre
     * SUPER_PAIR en MEMBER, dans le cache mémoire + disque.
     *
     * Appelé lors d'un SWAP de Super-Pair post-Bully via COORDINATOR (continuité FR-11.8, SANS
     * re-JOIN). Sans ça, le nouveau SP reste `role = MEMBER` dans l'inMemory des membres (il
     * l'était au JOIN_ACCEPT initial) → [ProcessMemberUpdateUseCase] rejette ses keepalives
     * ("fromNodeId pas SP courant") → `markSpSeen()` jamais rappelé → faux SP_TIMEOUT après 120s
     * → ré-élection en boucle / split-brain. Chicken-and-egg : le keepalive qui promouvrait le
     * nouveau SP est précisément celui qui est rejeté faute de promotion préalable.
     *
     * No-op si [newSpNodeId] est absent du cache : le membre ne connaît pas sa pubkey et devra
     * re-JOIN (chemin NewCandidateDetected → seedFromJoinAccept) pour la récupérer. La pubkey
     * conservée lors de la promotion est celle de l'entrée MEMBER existante (= celle avec
     * laquelle le nouveau SP signe ses MEMBER_UPDATE, posée à son JOIN).
     */
    suspend fun promoteSuperPair(newSpNodeId: ByteArray) {
        val updated = updateMutex.withLock {
            val current = _inMemory.value
            if (current.none { it.nodeId.contentEquals(newSpNodeId) }) return@withLock null
            val next = current.map { m ->
                when {
                    m.nodeId.contentEquals(newSpNodeId) ->
                        if (m.role == MemberRole.SUPER_PAIR) m else m.copy(role = MemberRole.SUPER_PAIR)
                    m.role == MemberRole.SUPER_PAIR -> m.copy(role = MemberRole.MEMBER)
                    else -> m
                }
            }
            if (next == current) return@withLock null
            _inMemory.value = next
            next
        } ?: return
        scope.launch {
            val clusterId = runCatching {
                nodeSettingsRepository.observeSettings().first().clusterId
            }.getOrNull()
            if (clusterId.isNullOrBlank()) return@launch
            val entity = snapshotDao.get(clusterId) ?: return@launch
            snapshotDao.upsert(
                entity.copy(
                    lastUpdatedMs = System.currentTimeMillis(),
                    membersJson = Json.encodeToString(updated)
                )
            )
        }
    }

    suspend fun loadFromDisk(clusterId: String): List<MemberInfo>? {
        val entity = snapshotDao.get(clusterId) ?: run {
            return null
        }
        return runCatching {
            Json.decodeFromString<List<MemberInfo>>(entity.membersJson)
        }.getOrNull()?.also { _inMemory.value = it }
    }

    fun snapshot(): List<MemberInfo> = _inMemory.value

    /** Vide le registre en mémoire (abdication SP, transition vers Undiscovered). */
    fun clearMembers() {
        _inMemory.value = emptyList()
        _inMemoryClusterId = ""
    }
}
