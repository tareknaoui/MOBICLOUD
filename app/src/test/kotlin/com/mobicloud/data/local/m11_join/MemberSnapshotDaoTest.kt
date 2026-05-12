package com.mobicloud.data.local.m11_join

import com.mobicloud.data.local.dao.MemberSnapshotDao
import com.mobicloud.data.local.entity.MemberSnapshotEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MemberSnapshotDaoTest {

    private lateinit var dao: MemberSnapshotDao

    private val snapshot = MemberSnapshotEntity(
        clusterId = "cluster-1",
        superPairNodeIdHex = "aabbccdd",
        lastUpdatedMs = 1000L,
        membersJson = "[]"
    )

    @Before
    fun setUp() {
        dao = mockk()
    }

    @Test
    fun `upsert insere le snapshot`() = runTest {
        coEvery { dao.upsert(snapshot) } returns Unit
        dao.upsert(snapshot)
        coVerify { dao.upsert(snapshot) }
    }

    @Test
    fun `get retourne le snapshot existant`() = runTest {
        coEvery { dao.get("cluster-1") } returns snapshot
        val result = dao.get("cluster-1")
        assertEquals("cluster-1", result?.clusterId)
        assertEquals("aabbccdd", result?.superPairNodeIdHex)
    }

    @Test
    fun `get retourne null si cluster inconnu`() = runTest {
        coEvery { dao.get("unknown") } returns null
        assertNull(dao.get("unknown"))
    }

    @Test
    fun `delete supprime le snapshot`() = runTest {
        coEvery { dao.delete("cluster-1") } returns Unit
        dao.delete("cluster-1")
        coVerify { dao.delete("cluster-1") }
    }
}
