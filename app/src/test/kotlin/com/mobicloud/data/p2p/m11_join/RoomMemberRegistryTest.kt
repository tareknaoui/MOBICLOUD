package com.mobicloud.data.p2p.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.entity.MemberEntity
import com.mobicloud.domain.models.NodeSettings
import com.mobicloud.domain.models.m11_join.MemberInfo
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NodeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoomMemberRegistryTest {

    private lateinit var memberDao: MemberDao
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var registry: RoomMemberRegistry

    private val nodeIdBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
    private val nodeIdHex = nodeIdBytes.toHexString().lowercase()
    private val member = MemberInfo(nodeIdBytes, byteArrayOf(1, 2), "1.2.3.4", 9090, 500L, MemberRole.MEMBER)

    private val activeEntity = MemberEntity(nodeIdHex, "cid", byteArrayOf(1, 2), "1.2.3.4", 9090, 500L, 1000L, "MEMBER", "ACTIVE")

    @Before
    fun setUp() {
        memberDao = mockk(relaxed = true)
        nodeSettingsRepository = mockk()
        every { nodeSettingsRepository.observeSettings() } returns flowOf(NodeSettings(0L, "cid"))
        registry = RoomMemberRegistry(memberDao, nodeSettingsRepository)
    }

    @Test
    fun `list recupere les membres actifs`() = runTest {
        coEvery { memberDao.listActiveSnapshot("cid") } returns listOf(activeEntity)
        val list = registry.list()
        assertEquals(1, list.size)
    }

    @Test
    fun `add appelle insertOrReplace`() = runTest {
        coEvery { memberDao.insertOrReplace(any()) } returns Unit
        registry.add(member)
        coVerify { memberDao.insertOrReplace(any()) }
    }

    // H24 régression : remove scope par clusterId.
    @Test
    fun `remove appelle deleteByNodeId scope cluster`() = runTest {
        coEvery { memberDao.deleteByNodeId(nodeIdHex, "cid") } returns 1
        registry.remove(nodeIdBytes, "cid")
        coVerify { memberDao.deleteByNodeId(nodeIdHex, "cid") }
    }

    @Test
    fun `size retourne le nombre de membres actifs`() = runTest {
        coEvery { memberDao.listActiveSnapshot("cid") } returns listOf(activeEntity)
        assertEquals(1, registry.size())
    }

    @Test
    fun `list retourne vide si clusterId est vide`() = runTest {
        every { nodeSettingsRepository.observeSettings() } returns flowOf(NodeSettings(0L, ""))
        val list = registry.list()
        assertEquals(0, list.size)
    }
}
