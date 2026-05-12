package com.mobicloud.domain.models

sealed class RelayEvent {
    data object Connected : RelayEvent()
    data class BlockReceived(
        val fromNodeId: String,
        val blockId: String,
        val data: ByteArray
    ) : RelayEvent()
    data class Ack(val blockId: String) : RelayEvent()
    data class PeerList(val peers: List<RelayPeer>) : RelayEvent()
    data class Error(val message: String) : RelayEvent()
    data class Disconnected(val reason: String? = null) : RelayEvent()
    /**
     * Story 9.4 — un pair distant a émis REQUEST_BLOCK pour un blockId que ce nœud (Super-Pair)
     * héberge potentiellement. Le routing métier (lookup HostedBlock + réponse) est délégué au
     * use-case RespondToBlockRequest ; le client WSS se contente d'émettre l'événement.
     */
    data class BlockRequestForwarded(
        val fromNodeId: String,
        val blockId: String
    ) : RelayEvent()
}

data class RelayPeer(
    val nodeId: String,
    val ip: String,
    val port: Int,
    val reliabilityScore: Float,
    val lastSeen: Long,
    val isSuperPair: Boolean = false,
    val clusterId: String = "",
    val freeBytes: Long = 0L,
    // Story 10.1 — clé publique EC P-256 SPKI-DER encodée en Base64.
    val pubKeySpkiDerB64: String = "",
    // Story 12.1 — charge cluster (nombre de membres actifs) pour le load balancing.
    val currentMemberCount: Int = 0
)
