package com.mobicloud.data.p2p.tcp

// Bytes 0x01-0x03 réservés GossipChannel, 0x20-0x42 réservés BlockTransferChannel — 0x08/0x09 libres.
object DepartureChannel {
    const val DEPARTURE_NOTICE: Byte = 0x08
    // Fix P8 : 200 000 → 16 000 bytes (~250 blockIds × 64 bytes = 16 KB réaliste)
    const val MAX_DEPARTURE_PAYLOAD_BYTES = 16_000

    // Story 7.2 — canal du Super-Pair vers le nœud partant (plan de migration)
    const val MIGRATION_PLAN: Byte = 0x09
    // Round 3 review : bump 64K → 128K. Une directive plein-format (blockId 64 chars + nodeId 64
    // + ip 45 + port 5 + pubkeyHex ~64 + overhead Protobuf) approche ~250 bytes ; pour 250 blocs
    // (cap NOTICE) + signature 72 + marge Protobuf, 64K était trop serré (rejet silencieux).
    const val MAX_MIGRATION_PLAN_BYTES = 128_000

    // Story 7.3 — canal du Super-Pair vers un donneur (plan de réplication d'un bloc)
    const val REPLICATE_PLAN: Byte = 0x0A
    const val MAX_REPLICATE_PLAN_BYTES = 2_000  // 1 directive × ~250 bytes + signature 72 bytes + marge
}
