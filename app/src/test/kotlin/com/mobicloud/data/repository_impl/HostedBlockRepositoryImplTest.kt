package com.mobicloud.data.repository_impl

import android.content.Context
import com.mobicloud.data.local.dao.HostedBlockDao
import com.mobicloud.data.local.entity.HostedBlockEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Story 6.2 — Task 16. Tests JVM purs sur `getBlock()`. `HostedBlockDao` est mocké via mockk,
 * `Context.filesDir` pointe vers un répertoire temporaire fourni par JUnit `TemporaryFolder`.
 */
class HostedBlockRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dao: HostedBlockDao
    private lateinit var repo: HostedBlockRepositoryImpl
    private lateinit var blocksDir: File

    private val validBlockId = "a".repeat(64)

    @Before
    fun setUp() {
        context = mockk()
        dao = mockk()
        every { context.filesDir } returns tmp.root
        blocksDir = File(tmp.root, "blocks").also { it.mkdirs() }
        repo = HostedBlockRepositoryImpl(context, dao)
    }

    private fun fakeEntity(blockId: String, file: File, fragmentIndex: Int = 3, isParity: Boolean = false) =
        HostedBlockEntity(
            blockId = blockId,
            ownerId = "owner",
            fragmentIndex = fragmentIndex,
            isParity = isParity,
            filePath = file.absolutePath,
            sizeBytes = file.length()
        )

    // --- Test 1 : Happy path ---
    @Test
    fun `getBlock returns payload when block file exists`() = runTest {
        val ciphertext = ByteArray(128) { it.toByte() }
        val file = File(blocksDir, validBlockId).also { it.writeBytes(ciphertext) }
        coEvery { dao.getHostedBlock(validBlockId) } returns fakeEntity(validBlockId, file, fragmentIndex = 5, isParity = true)

        val result = repo.getBlock(validBlockId)

        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(validBlockId, payload!!.blockId)
        assertEquals(5, payload.fragmentIndex)
        assertTrue(payload.isParity)
        assertArrayEquals(ciphertext, payload.ciphertext)
    }

    // --- Test 2 : Bloc absent (DAO retourne null) ---
    @Test
    fun `getBlock returns null when DAO returns null`() = runTest {
        coEvery { dao.getHostedBlock(validBlockId) } returns null

        val result = repo.getBlock(validBlockId)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    // --- Test 3 : Entrée DB présente mais fichier disque manquant ---
    @Test
    fun `getBlock returns null when file does not exist on disk`() = runTest {
        val missing = File(blocksDir, "ghost-file")
        coEvery { dao.getHostedBlock(validBlockId) } returns fakeEntity(validBlockId, missing)

        val result = repo.getBlock(validBlockId)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    // --- Test 4 : blockId au format invalide ---
    @Test
    fun `getBlock returns null when blockId format is invalid`() = runTest {
        val invalid = "NOT_A_HEX_HASH"

        val result = repo.getBlock(invalid)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
}
