package com.mobicloud.data.p2p.websocket

internal object RelayMsg {
    const val AUTH: Byte           = 0x01
    const val AUTH_OK: Byte        = 0x02
    const val REGISTER_PEER: Byte  = 0x03
    const val GET_PEERS: Byte      = 0x04
    const val PEERS: Byte          = 0x05
    const val UPLOAD: Byte         = 0x06
    const val FORWARD: Byte        = 0x07
    const val ACK: Byte            = 0x08
    const val PING: Byte           = 0x09
    const val PONG: Byte           = 0x0A
    // JOIN (0x0B) : "je suis présent" — distinct de REGISTER_PEER ("je suis Super-Pair élu").
    // Permet à l'élection Bully de se déclencher car les nœuds JOIN ne se déclarent pas Super-Pair.
    const val JOIN: Byte           = 0x0B
    // Story 9.4 — pull inter-cluster : un requester demande un bloc à un Super-Pair distant.
    // La réponse revient via UPLOAD/FORWARD existant (pas de message dédié pour le retour).
    const val REQUEST_BLOCK: Byte           = 0x0C
    const val REQUEST_BLOCK_FORWARDED: Byte = 0x0D
    // SIGNAL (0x0E) : forwarding P2P éphémère pour Gossip DHT entre pairs (relay-only).
    const val SIGNAL: Byte          = 0x0E
    const val SIGNAL_RECEIVED: Byte = 0x0F
    // Bully election broadcast via relay — forward à toutes les sessions sauf l'émetteur.
    const val ELECTION_BROADCAST: Byte = 0x10
    const val ELECTION_RECEIVED: Byte  = 0x11
    const val ERROR: Byte          = 0xFF.toByte()
}
