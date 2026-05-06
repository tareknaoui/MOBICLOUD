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
    // Renvoyé par le serveur (Story Bully) — true si ce nœud a fait REGISTER_PEER (Super-Pair élu),
    // false s'il a juste fait JOIN (simple participant). Default false pour back-compat avec
    // d'anciennes réponses serveur qui ne contiennent pas ce champ.
    val isSuperPair: Boolean = false,
    // Story 9.2 — UUID v4 du cluster du Super-Pair distant ; "" si pair legacy/JOIN.
    val clusterId: String = "",
    // Story 9.2 — capacité libre snapshot du Super-Pair distant (octets) ; 0 si legacy/JOIN.
    val freeBytes: Long = 0L
)
