package com.mobicloud.domain.models.m11_join

sealed class JoinEvent {
    /** Un message COORDINATOR Bully a été reçu — le Super-Pair candidat est connu. */
    data class CoordinatorReceived(
        val senderNodeId: ByteArray,
        val clusterId: String,
        val gpsLatitude: Double?,
        val gpsLongitude: Double?,
        val maxRadiusMeters: Int
    ) : JoinEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CoordinatorReceived
            if (!senderNodeId.contentEquals(other.senderNodeId)) return false
            if (clusterId != other.clusterId) return false
            if (gpsLatitude != other.gpsLatitude) return false
            if (gpsLongitude != other.gpsLongitude) return false
            if (maxRadiusMeters != other.maxRadiusMeters) return false
            return true
        }

        override fun hashCode(): Int {
            var result = senderNodeId.contentHashCode()
            result = 31 * result + clusterId.hashCode()
            result = 31 * result + (gpsLatitude?.hashCode() ?: 0)
            result = 31 * result + (gpsLongitude?.hashCode() ?: 0)
            result = 31 * result + maxRadiusMeters
            return result
        }
    }

    /** Un `JoinAccept` a été reçu du Super-Pair. */
    data class JoinAcceptReceived(
        val accept: JoinResponse.JoinAccept
    ) : JoinEvent()

    /** Un `JoinRedirect` a été reçu du Super-Pair. */
    data class JoinRedirectReceived(
        val redirect: JoinResponse.JoinRedirect
    ) : JoinEvent()

    /** Tous les candidats ont été épuisés sans succès. */
    data object AllCandidatesExhausted : JoinEvent()

    /** Le timer `ISOLATION_BACKOFF_MS` s'est écoulé. */
    data object IsolationBackoffElapsed : JoinEvent()

    /** Un nouveau Super-Pair candidat a été détecté (multicast ou tracker). */
    data class NewCandidateDetected(val hint: SuperPeerHint) : JoinEvent()

    /** Timeout SP détecté (3 heartbeats manqués — Story 11.3). */
    data class SpTimeoutDetected(val superPairNodeId: ByteArray) : JoinEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SpTimeoutDetected
            return superPairNodeId.contentEquals(other.superPairNodeId)
        }

        override fun hashCode(): Int = superPairNodeId.contentHashCode()
    }

    /** Victoire Bully : ce nœud est élu Super-Pair. */
    data class BullyVictory(val clusterId: String) : JoinEvent()

    /** Abdication volontaire après 30 min (Story 3.3) — distinct de BullyLost (involontaire). */
    data object AbdicationTriggered : JoinEvent()

    /** Défaite Bully : un autre nœud prend le rôle Super-Pair. */
    data class BullyLost(val newSuperPairNodeId: ByteArray) : JoinEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BullyLost
            return newSuperPairNodeId.contentEquals(other.newSuperPairNodeId)
        }

        override fun hashCode(): Int = newSuperPairNodeId.contentHashCode()
    }
}
