package com.mobicloud.data.p2p.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.m11_join.toEntityOrNull
import com.mobicloud.data.local.m11_join.toMemberInfoList
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.usecase.m11_join.MemberRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation Room du [MemberRegistry] (Story 11.3 AC5).
 *
 * Interface suspend : pas de `runBlocking`, pas de fire-and-forget. Cf. H20+H21 review.
 * Le clusterId est résolu via [nodeSettingsRepository] à chaque appel ; en V5 un seul
 * cluster à la fois (mono-cluster par device).
 */
@Singleton
class RoomMemberRegistry @Inject constructor(
    private val memberDao: MemberDao,
    private val nodeSettingsRepository: NodeSettingsRepository
) : MemberRegistry {

    private suspend fun currentClusterId(): String =
        nodeSettingsRepository.observeSettings().first().clusterId

    override suspend fun list(): List<MemberInfo> {
        val clusterId = currentClusterId()
        if (clusterId.isBlank()) return emptyList()
        return memberDao.listActiveSnapshot(clusterId).toMemberInfoList()
    }

    override suspend fun add(m: MemberInfo) {
        val clusterId = currentClusterId()
        if (clusterId.isBlank()) return
        // P5 (review R2) : soft validation — un MemberInfo issu du wire (JoinAccept snapshot,
        // MEMBER_UPDATE LEFT) avec une longueur hex inattendue ne doit pas crasher le caller.
        // On skip silencieusement la ligne corrompue.
        val entity = m.toEntityOrNull(clusterId, lastSeen = System.currentTimeMillis()) ?: return
        memberDao.insertOrReplace(entity)
    }

    override suspend fun remove(nodeId: ByteArray, clusterId: String) {
        // H24 : scope par clusterId pour éviter les delete cross-cluster.
        memberDao.deleteByNodeId(nodeId.toHexString().lowercase(), clusterId)
    }

    override suspend fun size(): Int {
        val clusterId = currentClusterId()
        if (clusterId.isBlank()) return 0
        return memberDao.listActiveSnapshot(clusterId).size
    }

    // P7 review : check-and-add atomique côté SP pour empêcher 2 JOIN concurrents
    // de dépasser MAX_CLUSTER_SIZE. Mutex coroutine — pas de blocage thread.
    private val capacityMutex = Mutex()

    override suspend fun addIfBelowCapacity(m: MemberInfo, max: Int): Boolean = capacityMutex.withLock {
        val clusterId = currentClusterId()
        if (clusterId.isBlank()) return@withLock false
        val current = memberDao.listActiveSnapshot(clusterId)
        val alreadyMember = current.any { it.nodeId.equals(m.nodeId.toHexString().lowercase(), ignoreCase = true) }
        if (!alreadyMember && current.size >= max) return@withLock false
        val entity = m.toEntityOrNull(clusterId, lastSeen = System.currentTimeMillis()) ?: return@withLock false
        memberDao.insertOrReplace(entity)
        true
    }
}
