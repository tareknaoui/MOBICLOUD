package com.mobicloud.domain.repository

import com.mobicloud.domain.models.RelayPeer
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import kotlinx.coroutines.flow.StateFlow

interface SignalingRepository {
    /**
     * Snapshot mémoire de la dernière liste reçue via GET_PEERS.
     * Volatile (TTL serveur 60s) — NE PAS persister, NE PAS substituer à PeerRepository.
     * Utilisé par les use-cases inter-cluster (9.3, 9.4) pour consommer clusterId/freeBytes.
     * Initialement emptyList() avant le premier PeerList.
     */
    val latestPeers: StateFlow<List<RelayPeer>>

    /**
     * Annonce la présence du nœud sur le relais SANS revendiquer le statut Super-Pair.
     * À utiliser au démarrage et en keepalive ; permet à Bully de se déclencher.
     */
    suspend fun joinAsParticipant(
        nodeId: String,
        ip: String? = null,
        port: Int? = null,
        reliabilityScore: Float,
        freeBytes: Long = 0L,
        totalBytes: Long = 0L
    ): Result<Unit>

    /** Enregistre ce nœud comme Super-Pair auprès des Serveurs Relais HA (post-Bully). */
    suspend fun registerAsSuperPeer(
        ip: String,
        port: Int,
        reliabilityScore: Float,
        electedAt: Long,
        nodeId: String
    ): Result<Unit>

    /** Déclenche GET_PEERS et insère les Super-Pairs reçus dans PeerRepository (source = RELAY_HA). */
    suspend fun fetchActiveSuperPeers(): Result<Unit>

    /**
     * Retourne les Super-Pairs connus du snapshot mémoire sous forme de [SuperPeerHint],
     * triés pour usage par [SendJoinRequestUseCase].
     * Additive — ne modifie PAS la signature de [fetchActiveSuperPeers].
     */
    suspend fun fetchActiveSuperPeerHints(): Result<List<SuperPeerHint>>

    /** Abdication explicite — le TTL 60s côté serveur purgera l'entrée automatiquement. */
    suspend fun unregisterAsSuperPeer(): Result<Unit>
}
