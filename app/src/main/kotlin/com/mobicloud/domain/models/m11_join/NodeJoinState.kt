package com.mobicloud.domain.models.m11_join

sealed class NodeJoinState {
    data object Undiscovered : NodeJoinState()

    data class Joining(
        val targetSuperPair: SuperPeerHint,
        val attemptIndex: Int
    ) : NodeJoinState()

    data class Member(
        val clusterId: String,
        val superPairNodeId: ByteArray
    ) : NodeJoinState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Member
            if (clusterId != other.clusterId) return false
            if (!superPairNodeId.contentEquals(other.superPairNodeId)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = clusterId.hashCode()
            result = 31 * result + superPairNodeId.contentHashCode()
            return result
        }
    }

    data class SuperPair(val clusterId: String) : NodeJoinState()

    // H9 : clusterId préservé pendant le re-join. Sans lui, `Rejoining → BullyLost → Member`
    // perdait l'appartenance au cluster (cluster_id reset à "") et le membre devenait orphelin.
    // P9 (review R2) : init guard pour interdire un clusterId vide qui réintroduirait le bug H9
    // sans crash visible (vide compile, mais sémantiquement rompu).
    data class Rejoining(val reason: RejoinReason, val clusterId: String) : NodeJoinState() {
        init { require(clusterId.isNotBlank()) { "Rejoining.clusterId ne peut pas être vide (H9 guard)" } }
    }

    data class Isolated(
        val rejectionCount: Int,
        val lastRejectionTimeMs: Long,
        val lastRejectedSPId: String = ""
    ) : NodeJoinState()
}

enum class RejoinReason { SP_TIMEOUT, SP_ABDICATION }
