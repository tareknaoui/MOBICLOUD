package com.mobicloud.data.local.m11_join

import com.mobicloud.data.local.dao.MemberDao
import com.mobicloud.data.local.entity.MemberEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemberDaoTest {

    private lateinit var dao: MemberDao

    private val entity = MemberEntity(
        nodeId = "aabbccdd",
        clusterId = "cluster-1",
        publicKeyBytes = byteArrayOf(1, 2, 3),
        ipAddress = "192.168.1.10",
        port = 9090,
        freeBytes = 1_000_000L,
        lastSeen = 1_000L,
        role = "MEMBER",
        status = "ACTIVE"
    )

    @Before
    fun setUp() {
        dao = mockk()
    }

    @Test
    fun `insertOrReplace est appele correctement`() = runTest {
        coEvery { dao.insertOrReplace(entity) } returns Unit
        dao.insertOrReplace(entity)
        coVerify { dao.insertOrReplace(entity) }
    }

    @Test
    fun `findByNodeId retourne l entite existante`() = runTest {
        coEvery { dao.findByNodeId("aabbccdd") } returns entity
        val result = dao.findByNodeId("aabbccdd")
        assertEquals(entity.nodeId, result?.nodeId)
        assertEquals(entity.clusterId, result?.clusterId)
    }

    @Test
    fun `findByNodeId retourne null si inconnu`() = runTest {
        coEvery { dao.findByNodeId("unknown") } returns null
        assertNull(dao.findByNodeId("unknown"))
    }

    @Test
    fun `touchHeartbeat retourne 1 sur mise a jour reussie`() = runTest {
        coEvery { dao.touchHeartbeat("aabbccdd", 2_000L, 999L, "10.0.0.1", 9091) } returns 1
        val rows = dao.touchHeartbeat("aabbccdd", 2_000L, 999L, "10.0.0.1", 9091)
        assertEquals(1, rows)
    }

    @Test
    fun `touchHeartbeat retourne 0 si membre absent`() = runTest {
        coEvery { dao.touchHeartbeat("unknown", any(), any(), any(), any()) } returns 0
        val rows = dao.touchHeartbeat("unknown", 0L, 0L, "", 0)
        assertEquals(0, rows)
    }

    @Test
    fun `markEvicted bascule le statut`() = runTest {
        coEvery { dao.markEvicted("aabbccdd") } returns 1
        val rows = dao.markEvicted("aabbccdd")
        assertEquals(1, rows)
    }

    @Test
    fun `purgeOlderThan respecte le cutoff`() = runTest {
        coEvery { dao.purgeOlderThan(1_000L) } returns 1
        val deleted = dao.purgeOlderThan(1_000L)
        assertEquals(1, deleted)
    }

    @Test
    fun `listByClusterId Flow emet sur insert`() = runTest {
        coEvery { dao.listByClusterId("cluster-1") } returns flowOf(listOf(entity))
        val result = dao.listByClusterId("cluster-1").first()
        assertTrue(result.isNotEmpty())
        assertEquals("aabbccdd", result[0].nodeId)
    }
}
