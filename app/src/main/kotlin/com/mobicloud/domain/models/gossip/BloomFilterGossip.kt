package com.mobicloud.domain.models.gossip

import kotlinx.serialization.Serializable

/**
 * Résumé (digest) du catalogue DHT d'un nœud, envoyé à chaque round de gossip.
 *
 * Compatibilité wire : les champs n'ont pas de @ProtoNumber explicite, ils sont donc
 * numérotés dans l'ordre de déclaration. Les champs ajoutés en v1 sont placés à la fin
 * avec une valeur par défaut, pour qu'une ancienne version les ignore.
 *
 * - v0 (legacy) : éléments = blockId, hachage déterministe, [partitionIds] = liste complète.
 * - v1 : éléments = clé versionnée (voir GossipSyncUseCase.versionKey), [salt] aléatoire
 *   par round, [partitionIds] vide (la liste complète annulait le gain du filtre).
 */
@Serializable
data class BloomFilterGossip(
    val senderNodeId: String,
    val bloomFilterBytes: ByteArray,
    val bloomFilterSize: Int = 1024,
    val numHashFunctions: Int = 3,
    val partitionIds: List<String>,
    val timestamp: Long,
    // ── v1 ──
    val salt: Long = 0L,
    val protocolVersion: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BloomFilterGossip) return false
        return senderNodeId == other.senderNodeId &&
            bloomFilterBytes.contentEquals(other.bloomFilterBytes) &&
            bloomFilterSize == other.bloomFilterSize &&
            numHashFunctions == other.numHashFunctions &&
            partitionIds == other.partitionIds &&
            timestamp == other.timestamp &&
            salt == other.salt &&
            protocolVersion == other.protocolVersion
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + bloomFilterBytes.contentHashCode()
        result = 31 * result + bloomFilterSize
        result = 31 * result + numHashFunctions
        result = 31 * result + partitionIds.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + salt.hashCode()
        result = 31 * result + protocolVersion
        return result
    }
}

/**
 * Résumé du catalogue du demandeur, transporté dans un [DeltaSyncRequest] v1 pour que
 * le répondeur renvoie ce qui manque au demandeur (sens inverse de l'échange).
 */
@Serializable
data class BloomDigest(
    val bytes: ByteArray,
    val size: Int,
    val numHashFunctions: Int,
    val salt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BloomDigest) return false
        return bytes.contentEquals(other.bytes) && size == other.size &&
            numHashFunctions == other.numHashFunctions && salt == other.salt
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + size
        result = 31 * result + numHashFunctions
        result = 31 * result + salt.hashCode()
        return result
    }
}
