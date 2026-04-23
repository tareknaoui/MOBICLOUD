package com.mobicloud.data.p2p.tcp

// Bytes 0x01-0x03 réservés GossipChannel, 0x20-0x42 réservés BlockTransferChannel — 0x08/0x09 libres.
object DepartureChannel {
    const val DEPARTURE_NOTICE: Byte = 0x08
    // Fix P8 : 200 000 → 16 000 bytes (~250 blockIds × 64 bytes = 16 KB réaliste)
    const val MAX_DEPARTURE_PAYLOAD_BYTES = 16_000

    // Story 7.2 — canal du Super-Pair vers le nœud partant (plan de migration)
    const val MIGRATION_PLAN: Byte = 0x09
    const val MAX_MIGRATION_PLAN_BYTES = 64_000  // ~250 directives × 250 bytes
}
