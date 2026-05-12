package com.mobicloud.data.p2p.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.m11_join.toEntity
import com.mobicloud.data.local.m11_join.toMemberInfoList
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.usecase.m11_join.MemberRegistry
import kotlinx.coroutines.flow.first
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
        memberDao.insertOrReplace(m.toEntity(clusterId, lastSeen = System.currentTimeMillis()))
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
}
