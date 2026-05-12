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
    val freeBytes: Long = 0L,
    // Story 10.1 — clé publique EC P-256 SPKI-DER encodée en Base64, telle que reçue dans GET_PEERS.
    // Vide ("") si le serveur ne la transporte pas (ancien relais) ou si la session WebSocket
    // du nœud est fermée au moment du GET_PEERS. Permet à BlockTransferClient de vérifier
    // la signature ACK inter-cluster sans ByteArray(0).
    val pubKeySpkiDerB64: String = "",
    // Story 11.1 — coordonnées GPS snapshot du Super-Pair distant ; null si absent ou legacy.
    // Volatile (snapshot session WebSocket) — non persisté en Room, consommé en mémoire par Story 11.2.
    // null interdit "0.0,0.0" comme sentinelle (coordonnée valide au large du Ghana).
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null
)
