package com.mobicloud.domain.models

import kotlinx.serialization.Serializable

/**
 * Payload sérialisé en Protobuf pour le protocole d'élection Bully.
 *
 * [timestampMs] est inclus dans la signature pour empêcher le replay d'un payload
 * capturé d'une session précédente (fenêtre validée à la réception, voir
 * [BULLY_TIMESTAMP_WINDOW_MS]).
 *
 * Story 12.1 : champs GPS ([gpsLatitude], [gpsLongitude], [maxRadiusMeters]) supprimés.
 * L'admission cluster est désormais basée sur [memberCount] uniquement.
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
 * Bytes canoniques signés pour TOUT message Bully.
 *
 * Story 12.1 : format mis à jour — GPS retiré.
 * Format : `v2|<type>|<nodeId>|<scoreBits>|<clusterId>|<timestampMs>`
 */
fun electionSignedBytes(
    type: ElectionMessageType,
    senderNodeId: String,
    reliabilityScore: Float,
    clusterId: String,
    timestampMs: Long
): ByteArray = buildString {
    append("v2|${type.name}|$senderNodeId|${reliabilityScore.toRawBits()}|$clusterId|$timestampMs")
}.toByteArray(Charsets.UTF_8)

/** Retourne les bytes canoniques v2 du payload pour vérification de signature. */
fun verifyElectionSignature(payload: ElectionPayload): ByteArray = electionSignedBytes(
    type = payload.type,
    senderNodeId = payload.senderNodeId,
    reliabilityScore = payload.reliabilityScore,
    clusterId = payload.clusterId,
    timestampMs = payload.timestampMs
)
