package com.mobicloud.domain.repository

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.RelayPeer
import kotlinx.coroutines.flow.StateFlow

interface RelayRepository {
    /** État de la connexion active (pour CloudRelayBadge Story 8.3). */
    val connectionState: StateFlow<RelayConnectionState>

    /** Upload un bloc chiffré via le relais HA. Résout via ACK ou Failure. */
    suspend fun uploadBlock(destNodeId: String, blockId: String, data: ByteArray): Result<Unit>

    /** Récupère la liste des Super-Pairs connus du relais. */
    suspend fun fetchSuperPeers(): Result<List<RelayPeer>>

    /**
     * Story 9.4 — pull inter-cluster : demande [blockId] au Super-Pair distant [remoteNodeId]
     * via le canal REQUEST_BLOCK / FORWARD. Bloque jusqu'à réception ou timeout.
     *
     * @return Result.success(BlockTransferMessage) si la réponse arrive et désérialise correctement,
     *         Result.failure(SocketTimeoutException) si pas de réponse en [timeoutMs],
     *         Result.failure(IllegalStateException) si la connexion relais n'est pas active
     *         ou si une requête est déjà en cours pour [blockId].
     */
    suspend fun requestBlock(
        remoteNodeId: String,
        blockId: String,
        timeoutMs: Long
    ): Result<BlockTransferMessage>

    /**
     * Annonce au relay dashboard quels fragments de [entry] sont distribués sur quels nœuds.
     * Best-effort : un échec ne doit pas bloquer l'upload.
     */
    suspend fun announceFragments(entry: CatalogEntry, uploaderNodeId: String = ""): Result<Unit>
}

enum class RelayConnectionState { CONNECTING, CONNECTED, RELAY_HA, OFFLINE }
