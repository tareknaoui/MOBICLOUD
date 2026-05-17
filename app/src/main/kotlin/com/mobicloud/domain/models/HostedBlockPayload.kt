package com.mobicloud.domain.models

/**
 * Story 6.2 — bloc lu depuis le stockage local côté hoster, prêt à être renvoyé via TCP.
 * Story 6.3 — étendu avec [iv] (12 bytes AES-GCM nonce) propagé jusqu'au client.
 */
data class HostedBlockPayload(
    val blockId: String,
    val ownerId: String,
    val fragmentIndex: Int,
    val isParity: Boolean,
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    init {
        require(iv.size == 12) { "iv must be 12 bytes (AES-GCM nonce), got ${iv.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HostedBlockPayload) return false
        return blockId == other.blockId &&
                ownerId == other.ownerId &&
                fragmentIndex == other.fragmentIndex &&
                isParity == other.isParity &&
                ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + isParity.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
