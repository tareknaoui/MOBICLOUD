package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.MemberInfo

/**
 * Registre des membres du cluster côté Super-Pair.
 * En V5 (Story 11.2) : implémentation RAM uniquement.
 * Story 11.3 swappera l'impl vers Room `cluster_members` sans toucher [ProcessJoinRequestUseCase].
 */
interface MemberRegistry {
    fun list(): List<MemberInfo>
    fun add(m: MemberInfo)
    fun remove(nodeId: ByteArray)
    val size: Int
}

// @Singleton garantit que tous les use cases (ProcessJoinRequestUseCase,
// MarkSelfAsSuperPairUseCase, BullySoloElectionUseCase) partagent la même
// instance — sinon, deux registres divergents (cas du double-new dans le service).
@javax.inject.Singleton
class RamMemberRegistry @javax.inject.Inject constructor() : MemberRegistry {
    private val lock = Any()
    private val _members = mutableListOf<MemberInfo>()

    override fun list(): List<MemberInfo> = synchronized(lock) { _members.toList() }

    override fun add(m: MemberInfo): Unit = synchronized(lock) {
        _members.removeAll { it.nodeId.contentEquals(m.nodeId) }
        _members.add(m)
        Unit
    }

    override fun remove(nodeId: ByteArray) = synchronized(lock) {
        _members.removeAll { it.nodeId.contentEquals(nodeId) }
        Unit
    }

    override val size: Int get() = synchronized(lock) { _members.size }
}
