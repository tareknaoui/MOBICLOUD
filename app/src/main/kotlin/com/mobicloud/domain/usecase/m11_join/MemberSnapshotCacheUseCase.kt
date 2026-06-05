package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberSnapshotDao
import com.mobicloud.data.local.entity.MemberSnapshotEntity
import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.m11_join.MemberInfo
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
