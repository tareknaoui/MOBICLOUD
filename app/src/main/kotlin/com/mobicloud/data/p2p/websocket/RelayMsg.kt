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
    const val ERROR: Byte          = 0xFF.toByte()
}
