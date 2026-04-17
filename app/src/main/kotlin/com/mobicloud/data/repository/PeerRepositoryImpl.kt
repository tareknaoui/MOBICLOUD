package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.PeerDao
import com.mobicloud.data.local.entity.PeerNodeEntity
import com.mobicloud.data.local.entity.toDomain
import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.PeerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class PeerRepositoryImpl @Inject constructor(
    private val peerDao: PeerDao,
    private val externalScope: CoroutineScope
) : PeerRepository {

    override val peers: StateFlow<List<Peer>> = peerDao.getAllPeers()
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    override suspend fun registerOrUpdatePeer(
        identity: NodeIdentity,
        timestampMs: Long,
        source: DiscoverySource,
        ipAddress: String?,
        port: Int?,
        isSuperPair: Boolean
    ): Result<Unit> = runCatching {
        peerDao.insertOrUpdatePreservingRole(
            nodeId = identity.nodeId,
            publicKeyBytes = identity.publicKeyBytes,
            reliabilityScore = identity.reliabilityScore,
            ipAddress = ipAddress,
            port = port,
            timestampMs = timestampMs,
            source = source.name,
            isSuperPair = if (isSuperPair) 1 else 0
        )
    }

    override suspend fun evictStalePeers(timeoutMs: Long, currentTimeMs: Long): Result<Unit> =
        runCatching { peerDao.markInactive(currentTimeMs - timeoutMs) }
}
