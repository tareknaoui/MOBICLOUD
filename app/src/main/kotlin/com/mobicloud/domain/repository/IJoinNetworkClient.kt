package com.mobicloud.domain.repository

import com.mobicloud.domain.models.m11_join.JoinRequest
import com.mobicloud.domain.models.m11_join.JoinResponse
import com.mobicloud.domain.models.m11_join.SuperPeerHint
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface réseau JOIN — sœur de [IElectionNetworkClient].
 * Aucune dépendance Android/OkHttp dans le domain ; l'impl [JoinNetworkClientImpl]
 * détient les dépendances sur [RelayWebSocketClient] et le socket LAN.
 */
interface IJoinNetworkClient {
    /** Flux hot des messages JOIN entrants (FORWARD 0x07 + préfixe JoinSubType). */
    val incomingJoinRequests: SharedFlow<JoinIncomingMessage>

    /**
     * Envoie un [JoinRequest] au Super-Pair candidat et attend la réponse.
     * Timeout géré côté [SendJoinRequestUseCase] via `withTimeoutOrNull`.
     */
    suspend fun sendJoinRequest(hint: SuperPeerHint, request: JoinRequest): Result<JoinResponse>
}

/** Message entrant préfixé par un [JoinSubType] reçu via FORWARD. */
data class JoinIncomingMessage(
    val fromNodeId: String,
    val subTypeByte: Byte,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as JoinIncomingMessage
        if (fromNodeId != other.fromNodeId) return false
        if (subTypeByte != other.subTypeByte) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fromNodeId.hashCode()
        result = 31 * result + subTypeByte
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
