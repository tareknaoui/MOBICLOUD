package com.mobicloud.data.p2p.tcp

object BlockTransferChannel {
    // Ne pas confliciter avec GossipChannel : 0x01, 0x02, 0x03
    const val BLOCK_TRANSFER: Byte = 0x20
    const val BLOCK_ACK: Byte = 0x21
    const val BLOCK_NACK: Byte = 0x22
    const val CONNECT_TIMEOUT_MS = 5_000
    const val BASE_ACK_TIMEOUT_MS = 10_000L
    const val MAX_ACK_TIMEOUT_MS = 30_000L
    // Limite ACK message — le payload ACK est petit (<1 KB)
    const val MAX_ACK_PAYLOAD_BYTES = 4_096
    // Limite payload BLOCK_TRANSFER — 1 MiB ciphertext + 16 tag GCM + overhead proto
    const val MAX_BLOCK_PAYLOAD_BYTES = 2_000_000
}
