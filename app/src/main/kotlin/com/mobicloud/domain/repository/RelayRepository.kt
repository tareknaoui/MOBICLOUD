package com.mobicloud.domain.repository

import com.mobicloud.domain.models.RelayPeer
import kotlinx.coroutines.flow.StateFlow

interface RelayRepository {
    /** État de la connexion active (pour CloudRelayBadge Story 8.3). */
    val connectionState: StateFlow<RelayConnectionState>

    /** Upload un bloc chiffré via le relais HA. Résout via ACK ou Failure. */
    suspend fun uploadBlock(destNodeId: String, blockId: String, data: ByteArray): Result<Unit>

    /** Récupère la liste des Super-Pairs connus du relais. */
    suspend fun fetchSuperPeers(): Result<List<RelayPeer>>
}

enum class RelayConnectionState { CONNECTING, CONNECTED, RELAY_HA, OFFLINE }
