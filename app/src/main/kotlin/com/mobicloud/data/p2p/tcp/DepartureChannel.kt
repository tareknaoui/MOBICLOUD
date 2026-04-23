package com.mobicloud.data.p2p.tcp

// Bytes 0x01-0x03 réservés GossipChannel, 0x20-0x42 réservés BlockTransferChannel — 0x08 libre.
object DepartureChannel {
    const val DEPARTURE_NOTICE: Byte = 0x08
    // Fix P8 : 200 000 → 16 000 bytes (~250 blockIds × 64 bytes = 16 KB réaliste)
    const val MAX_DEPARTURE_PAYLOAD_BYTES = 16_000
    // Fix P10 : DEPARTURE_ACK supprimé — dead code Story 7.1.
    // Le byte 0x09 est réservé pour Story 7.2 (confirmation Super-Pair) — à redéclarer là-bas.
}
