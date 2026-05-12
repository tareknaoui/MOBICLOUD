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

    data class Rejoining(val reason: RejoinReason) : NodeJoinState()

    data class Isolated(
        val rejectionCount: Int,
        val lastRejectionTimeMs: Long
    ) : NodeJoinState()
}

enum class RejoinReason { SP_TIMEOUT, SP_ABDICATION }
