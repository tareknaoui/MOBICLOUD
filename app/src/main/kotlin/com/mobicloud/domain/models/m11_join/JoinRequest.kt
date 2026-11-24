package com.mobicloud.domain.models.m11_join

import com.mobicloud.domain.models.EncryptionKeyBinding
import kotlinx.serialization.Serializable

@Serializable
data class JoinRequest(
    val senderNodeId: ByteArray,
    val candidatePublicKey: ByteArray,
    val freeBytes: Long,
    val reliabilityScore: Float,
    val timestampMs: Long,
    val signatureBytes: ByteArray,
    /**
     * Clé de chiffrement signée par la même clé d'identité (R2-10). Champ ajouté en fin de
     * message : un ancien Super-Pair l'ignore, un ancien candidat ne l'envoie pas.
     */
    val encryptionKeyBinding: EncryptionKeyBinding? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as JoinRequest
        if (!senderNodeId.contentEquals(other.senderNodeId)) return false
        if (!candidatePublicKey.contentEquals(other.candidatePublicKey)) return false
        if (freeBytes != other.freeBytes) return false
        if (reliabilityScore != other.reliabilityScore) return false
        if (timestampMs != other.timestampMs) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        if (encryptionKeyBinding != other.encryptionKeyBinding) return false
        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.contentHashCode()
        result = 31 * result + candidatePublicKey.contentHashCode()
        result = 31 * result + freeBytes.hashCode()
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        result = 31 * result + (encryptionKeyBinding?.hashCode() ?: 0)
        return result
    }
}
