package com.mobicloud.domain.models

/**
 * Story 6.2 — bloc téléchargé et vérifié, prêt pour le pipeline de déchiffrement (Story 6.3).
 * Story 6.3 — étendu avec [iv] (12 bytes AES-GCM nonce) propagé depuis [BlockResponseMessage].
 * Story 6.4 — étendu avec [latencyMs] (wall-clock réseau total, ms) pour l'UI de progression.
 *
 * Override `equals`/`hashCode` requis car [ciphertext] et [iv] sont des `ByteArray`.
 */
data class DownloadedBlock(
    val blockId: String,
    val fragmentIndex: Int,
    val isParity: Boolean,
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val latencyMs: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadedBlock) return false
        return blockId == other.blockId &&
                fragmentIndex == other.fragmentIndex &&
                isParity == other.isParity &&
                ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv) &&
                latencyMs == other.latencyMs
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + isParity.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + latencyMs.hashCode()
        return result
    }
}
