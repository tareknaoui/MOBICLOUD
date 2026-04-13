package com.mobicloud.domain.repository

import com.mobicloud.domain.models.Peer
import kotlinx.coroutines.flow.Flow

interface BootstrapRepository {
    /**
     * Annonce la présence du noeud local sur le serveur de bootstrap (Signaling).
     */
    suspend fun announcePresence(ip: String, port: Int): Result<Unit>

    /**
     * Observe en temps réel la liste des pairs actifs sur le réseau mondial (Signaling).
     */
    fun observeActivePeers(): Flow<List<Peer>>
}
