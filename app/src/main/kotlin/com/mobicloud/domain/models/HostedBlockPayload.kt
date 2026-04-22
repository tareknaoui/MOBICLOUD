package com.mobicloud.domain.models

/**
 * Story 6.2 — bloc lu depuis le stockage local côté hoster, prêt à être renvoyé via TCP.
 */
data class HostedBlockPayload(
    val blockId: String,
    val fragmentIndex: Int,
    val isParity: Boolean,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HostedBlockPayload) return false
        return blockId == other.blockId &&
                fragmentIndex == other.fragmentIndex &&
                isParity == other.isParity &&
                ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + isParity.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
