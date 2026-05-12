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

    /**
     * P7 review (Story 12.1) : check-and-add atomique pour borner strictement la taille
     * du cluster. Sans ça, deux JOIN concurrents franchissent tous deux le check `size() < max`
     * puis appellent `add()` → cluster à `max + 1`.
     * @return true si le membre a été ajouté, false si le cluster est saturé.
     */
    suspend fun addIfBelowCapacity(m: MemberInfo, max: Int): Boolean
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

    override suspend fun addIfBelowCapacity(m: MemberInfo, max: Int): Boolean = synchronized(lock) {
        // Le check ignore un doublon de l'expéditeur (re-JOIN après timeout) — ne pas le compter
        // dans la capacité pour ne pas le rejeter à tort.
        val existing = _members.indexOfFirst { it.nodeId.contentEquals(m.nodeId) }
        if (existing < 0 && _members.size >= max) return@synchronized false
        if (existing >= 0) _members[existing] = m else _members.add(m)
        true
    }
}
