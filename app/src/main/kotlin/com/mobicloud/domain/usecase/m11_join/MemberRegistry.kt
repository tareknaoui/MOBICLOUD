package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.MemberInfo

/**
 * Registre des membres du cluster côté Super-Pair.
 *
 * Story 11.3 review : interface passée en `suspend` (H20+H21) pour :
 * - éviter `runBlocking` sur dispatcher IO (RoomMemberRegistry.list/size bloquait un thread IO),
 * - sérialiser `add` proprement (le fire-and-forget précédent racait avec `list()` au build du JoinAccept).
 */
interface MemberRegistry {
    suspend fun list(): List<MemberInfo>
    suspend fun add(m: MemberInfo)
    suspend fun remove(nodeId: ByteArray, clusterId: String)
    suspend fun size(): Int
}

// Conservé pour les tests JVM purs (sans Room) ; en prod, RoomMemberRegistry est utilisé via Hilt @Binds.
@javax.inject.Singleton
class RamMemberRegistry @javax.inject.Inject constructor() : MemberRegistry {
    private val lock = Any()
    private val _members = mutableListOf<MemberInfo>()

    override suspend fun list(): List<MemberInfo> = synchronized(lock) { _members.toList() }

    override suspend fun add(m: MemberInfo): Unit = synchronized(lock) {
        _members.removeAll { it.nodeId.contentEquals(m.nodeId) }
        _members.add(m)
        Unit
    }

    override suspend fun remove(nodeId: ByteArray, clusterId: String) = synchronized(lock) {
        _members.removeAll { it.nodeId.contentEquals(nodeId) }
        Unit
    }

    override suspend fun size(): Int = synchronized(lock) { _members.size }
}
