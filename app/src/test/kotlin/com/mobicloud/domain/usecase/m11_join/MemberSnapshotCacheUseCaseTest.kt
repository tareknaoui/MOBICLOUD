package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.data.local.dao.MemberSnapshotDao
import com.mobicloud.data.local.entity.MemberSnapshotEntity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.repository.NodeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemberSnapshotCacheUseCaseTest {

    private lateinit var snapshotDao: MemberSnapshotDao
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val nodeA = byteArrayOf(0xAA.toByte())
    private val nodeB = byteArrayOf(0xBB.toByte())
    private val spNode = byteArrayOf(0x11.toByte())
    private val sig = byteArrayOf(0x01)

    private fun member(nodeId: ByteArray) =
        MemberInfo(nodeId, sig, "1.2.3.4", 80, null, null, 100L, MemberRole.MEMBER)

    @Before
    fun setUp() {
        snapshotDao = mockk(relaxed = true)
        nodeSettingsRepository = mockk(relaxed = true)
        every { nodeSettingsRepository.observeSettings() } returns flowOf(NodeSettings(0L, "cid"))
    }

    private fun makeUseCase(scope: TestScope) =
        MemberSnapshotCacheUseCase(snapshotDao, nodeSettingsRepository, scope)

    // seedFromJoinAccept insère + inMemory rempli
    @Test
    fun `seedFromJoinAccept insere dans dao et remplit inMemory`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        val snapshot = listOf(member(nodeA), member(nodeB))
        useCase.seedFromJoinAccept("cid", spNode, snapshot)
        assertEquals(2, useCase.inMemory.value.size)
        coVerify { snapshotDao.upsert(any()) }
    }

    // loadFromDisk lit depuis DAO
    @Test
    fun `loadFromDisk retourne la liste et remplit inMemory`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        val members = listOf(member(nodeA))
        val json = Json.encodeToString(members)
        coEvery { snapshotDao.get("cid") } returns MemberSnapshotEntity("cid", "1122", 1000L, json)
        val result = useCase.loadFromDisk("cid")
        assertEquals(1, result?.size)
        assertEquals(1, useCase.inMemory.value.size)
    }

    // loadFromDisk retourne null si clusterId inconnu
    @Test
    fun `loadFromDisk retourne null si cluster inconnu`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        coEvery { snapshotDao.get("unknown") } returns null
        assertNull(useCase.loadFromDisk("unknown"))
        assertTrue(useCase.inMemory.value.isEmpty())
    }

    // applyUpdate JOINED ajoute au StateFlow
    @Test
    fun `applyUpdate JOINED ajoute le membre`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        useCase.seedFromJoinAccept("cid", spNode, listOf(member(nodeA)))
        val update = MemberUpdate(MemberUpdateEvent.JOINED, member(nodeB), byteArrayOf(), 1000L, sig)
        useCase.applyUpdate(update)
        assertEquals(2, useCase.inMemory.value.size)
    }

    // applyUpdate LEFT retire du StateFlow
    @Test
    fun `applyUpdate LEFT retire le membre`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        useCase.seedFromJoinAccept("cid", spNode, listOf(member(nodeA), member(nodeB)))
        val update = MemberUpdate(MemberUpdateEvent.LEFT, null, nodeB, 2000L, sig)
        useCase.applyUpdate(update)
        assertEquals(1, useCase.inMemory.value.size)
        assertTrue(useCase.inMemory.value[0].nodeId.contentEquals(nodeA))
    }

    // C5 régression : applyUpdate doit PERSISTER le snapshot sur disque avec le bon clusterId
    // et le bon JSON. Avant fix, la requête utilisait toujours "" et le upsert ne se faisait pas.
    @Test
    fun `applyUpdate persiste le snapshot disque avec clusterId courant`() = runTest(testDispatcher) {
        val useCase = makeUseCase(this)
        useCase.seedFromJoinAccept("cid", spNode, listOf(member(nodeA)))
        coEvery { snapshotDao.get("cid") } returns MemberSnapshotEntity("cid", "11", 1L, "[]")

        val update = MemberUpdate(MemberUpdateEvent.JOINED, member(nodeB), byteArrayOf(), 1000L, sig)
        useCase.applyUpdate(update)

        // applyUpdate doit lire le clusterId via NodeSettings et upsert sur la même clé.
        // Avant fix C5, observeSettings n'était pas appelé du tout (clusterId vide hardcodé).
        coVerify { nodeSettingsRepository.observeSettings() }
        coVerify { snapshotDao.get("cid") }
        // Au moins 2 upserts : seed + applyUpdate
        coVerify(atLeast = 2) { snapshotDao.upsert(match { it.clusterId == "cid" }) }
        // Et le inMemory contient bien nodeB
        assertEquals(2, useCase.inMemory.value.size)
        assertTrue(useCase.inMemory.value.any { it.nodeId.contentEquals(nodeB) })
    }
}
