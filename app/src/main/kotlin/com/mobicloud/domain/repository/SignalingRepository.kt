package com.mobicloud.domain.repository

interface SignalingRepository {
    /** Enregistre ce nœud comme Super-Pair auprès des Serveurs Relais HA. */
    suspend fun registerAsSuperPeer(
        ip: String,
        port: Int,
        reliabilityScore: Float,
        electedAt: Long,
        nodeId: String
    ): Result<Unit>

    /** Déclenche GET_PEERS et insère les Super-Pairs reçus dans PeerRepository (source = RELAY_HA). */
    suspend fun fetchActiveSuperPeers(): Result<Unit>

    /** Abdication explicite — le TTL 60s côté serveur purgera l'entrée automatiquement. */
    suspend fun unregisterAsSuperPeer(): Result<Unit>
}
