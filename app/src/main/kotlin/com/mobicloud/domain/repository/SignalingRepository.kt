package com.mobicloud.domain.repository

import com.mobicloud.domain.models.Peer
import kotlinx.coroutines.flow.Flow

interface SignalingRepository {
    /** Enregistre le nœud local sur Firebase. Retourne Result.failure si inaccessible. */
    suspend fun registerNode(ip: String, port: Int): Result<Unit>

    /** Observe les nœuds distants sur Firebase (TTL 60s filtré, nœud local exclu). */
    fun observeRemoteNodes(): Flow<List<Peer>>
}
