package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

/**
 * Payload sérialisé en Protobuf pour le protocole d'élection Bully.
 *
 * [timestampMs] est inclus dans la signature pour empêcher le replay d'un payload
 * capturé d'une session précédente (fenêtre validée à la réception, voir
 * [BULLY_TIMESTAMP_WINDOW_MS]).
 */
@Serializable
data class ElectionPayload(
    val senderNodeId: String,
    val type: ElectionMessageType,
    val reliabilityScore: Float,
    val signatureBytes: ByteArray,
    val clusterId: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ElectionPayload

        if (senderNodeId != other.senderNodeId) return false
        if (type != other.type) return false
        if (reliabilityScore != other.reliabilityScore) return false
        if (!signatureBytes.contentEquals(other.signatureBytes)) return false
        if (clusterId != other.clusterId) return false
        if (timestampMs != other.timestampMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        result = 31 * result + clusterId.hashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}

/** Fenêtre anti-replay (±) pour la validation des timestamps Bully. */
const val BULLY_TIMESTAMP_WINDOW_MS: Long = 30_000L

/**
 * Bytes canoniques signés pour TOUT message Bully — sign et verify partagent
 * obligatoirement cette fonction.
 *
 * Format `v1|<type>|<nodeId>|<scoreBits>|<clusterId>|<timestampMs>` :
 *  - `v1|` préfixe versionné (futur-compat).
 *  - `score.toRawBits()` Int déterministe (pas de fragilité Float.toString locale).
 *  - `clusterId` vide pour ABDICATION/ELECTION/ALIVE — laisse la chaîne vide,
 *    pas omise, pour que le format reste fixe.
 *  - `timestampMs` lié au payload pour empêcher le replay.
 */
fun electionSignedBytes(
    type: ElectionMessageType,
    senderNodeId: String,
    reliabilityScore: Float,
    clusterId: String,
    timestampMs: Long
): ByteArray = "v1|${type.name}|$senderNodeId|${reliabilityScore.toRawBits()}|$clusterId|$timestampMs"
    .toByteArray(Charsets.UTF_8)
