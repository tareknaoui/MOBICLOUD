package com.mobicloud.domain.models

import com.mobicloud.domain.models.m11_join.MAX_RADIUS_METERS
import kotlinx.serialization.Serializable

/**
 * Payload sérialisé en Protobuf pour le protocole d'élection Bully.
 *
 * [timestampMs] est inclus dans la signature pour empêcher le replay d'un payload
 * capturé d'une session précédente (fenêtre validée à la réception, voir
 * [BULLY_TIMESTAMP_WINDOW_MS]).
 *
 * v2 — Story 11.2 : ajout des champs GPS optionnels ([gpsLatitude], [gpsLongitude],
 * [maxRadiusMeters]) renseignés uniquement pour COORDINATOR. Default values garantissent
 * la rétrocompatibilité avec les pairs v1 (lecture seule ; écriture toujours en v2).
 */
@Serializable
data class ElectionPayload(
    val senderNodeId: String,
    val type: ElectionMessageType,
    val reliabilityScore: Float,
    val signatureBytes: ByteArray,
    val clusterId: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
    // Champs v2 — renseignés uniquement pour COORDINATOR ; null/default pour ELECTION/ALIVE/ABDICATION
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val maxRadiusMeters: Int = MAX_RADIUS_METERS
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
        if (gpsLatitude != other.gpsLatitude) return false
        if (gpsLongitude != other.gpsLongitude) return false
        if (maxRadiusMeters != other.maxRadiusMeters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + reliabilityScore.hashCode()
        result = 31 * result + signatureBytes.contentHashCode()
        result = 31 * result + clusterId.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + (gpsLatitude?.hashCode() ?: 0)
        result = 31 * result + (gpsLongitude?.hashCode() ?: 0)
        result = 31 * result + maxRadiusMeters
        return result
    }
}

/** Fenêtre anti-replay (±) pour la validation des timestamps Bully. */
const val BULLY_TIMESTAMP_WINDOW_MS: Long = 30_000L

/**
 * Bytes canoniques signés pour TOUT message Bully — sign et verify partagent
 * obligatoirement cette fonction.
 *
 * Format v2 (Story 11.2) : `v2|<type>|<nodeId>|<scoreBits>|<clusterId>|<timestampMs>|<gpsLatBits>|<gpsLngBits>|<maxRadius>`
 *  - `v2|` bump versionné pour inclure les champs GPS dans la signature COORDINATOR.
 *  - `score.toRawBits()` Int déterministe (pas de fragilité Float.toString locale).
 *  - GPS null → "null" string (chaîne fixe déterministe pour ELECTION/ALIVE/ABDICATION).
 *  - [verifyElectionSignature] accepte v1 (legacy) ET v2 pendant la transition.
 */
fun electionSignedBytes(
    type: ElectionMessageType,
    senderNodeId: String,
    reliabilityScore: Float,
    clusterId: String,
    timestampMs: Long,
    gpsLatitude: Double? = null,
    gpsLongitude: Double? = null,
    maxRadiusMeters: Int = MAX_RADIUS_METERS
): ByteArray = buildString {
    append("v2|${type.name}|$senderNodeId|${reliabilityScore.toRawBits()}|$clusterId|$timestampMs")
    append("|${gpsLatitude?.toBits() ?: "null"}")
    append("|${gpsLongitude?.toBits() ?: "null"}")
    append("|$maxRadiusMeters")
}.toByteArray(Charsets.UTF_8)

// Story 11.2 décision review : fallback v1 supprimé. Le bump v1→v2 est coordonné émetteur+récepteur
// dans le même commit ; accepter v1 ouvre un trou (payload v2 avec GPS arbitraire + signature v1 valide
// → GPS non authentifié). Tout payload sans signature v2 valide est rejeté.

/** Retourne les bytes canoniques v2 du payload. La vérification de signature doit utiliser uniquement v2. */
fun verifyElectionSignature(payload: ElectionPayload): ByteArray = electionSignedBytes(
    type = payload.type,
    senderNodeId = payload.senderNodeId,
    reliabilityScore = payload.reliabilityScore,
    clusterId = payload.clusterId,
    timestampMs = payload.timestampMs,
    gpsLatitude = payload.gpsLatitude,
    gpsLongitude = payload.gpsLongitude,
    maxRadiusMeters = payload.maxRadiusMeters
)
