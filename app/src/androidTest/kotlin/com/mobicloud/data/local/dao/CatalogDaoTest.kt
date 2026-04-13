package com.mobicloud.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobicloud.data.local.CatalogDatabase
import com.mobicloud.data.local.entity.CatalogEntryEntity
import com.mobicloud.data.local.entity.FragmentLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDaoTest {

    private lateinit var database: CatalogDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun setup() {
        // Pas de allowMainThreadQueries() (BH-04) : les tests doivent utiliser Dispatchers.IO
        // pour reproduire le comportement de production et valider les guardrails du Repository.
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CatalogDatabase::class.java
        ).build()
        dao = database.catalogDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testInsertAndGetFlowWithFragments() = runBlocking {
        val hash = "hash_123"
        val entry = CatalogEntryEntity(
            fileHash = hash,
            ownerPubKeyHash = "pubkey_1",
            versionClock = 1L
        )
        val fragment = FragmentLocationEntity(
            catalogFileHash = hash,
            fragmentIndex = 0,
            fragmentHash = "frag_hash",
            nodeIds = "[\"node1\"]"
        )

        withContext(Dispatchers.IO) { dao.insertWithFragments(entry, listOf(fragment)) }

        val retrievedFlow = dao.getCatalogEntryFlow(hash).first()
        assertNotNull(retrievedFlow)
        assertEquals(hash, retrievedFlow?.catalogEntry?.fileHash)
        assertEquals("pubkey_1", retrievedFlow?.catalogEntry?.ownerPubKeyHash)
        assertEquals(1L, retrievedFlow?.catalogEntry?.versionClock)
        
        assertEquals(1, retrievedFlow?.fragmentLocations?.size)
        assertEquals(0, retrievedFlow?.fragmentLocations?.get(0)?.fragmentIndex)
        assertEquals("[\"node1\"]", retrievedFlow?.fragmentLocations?.get(0)?.nodeIds)
    }

    @Test
    fun testInsertAndGetSync() = runBlocking {
        val hash = "hash_sync"
        val entry = CatalogEntryEntity(hash, "pubkey_sync", 2L)

        withContext(Dispatchers.IO) { dao.insertWithFragments(entry, emptyList()) }

        val retrieved = withContext(Dispatchers.IO) { dao.getCatalogEntry(hash) }
        assertNotNull(retrieved)
        assertEquals(hash, retrieved?.catalogEntry?.fileHash)
        assertEquals("pubkey_sync", retrieved?.catalogEntry?.ownerPubKeyHash)
        assertEquals(2L, retrieved?.catalogEntry?.versionClock)
        assertEquals(0, retrieved?.fragmentLocations?.size)
    }

    @Test
    fun testUpdateEntryReplacesFragments() = runBlocking {
        val hash = "update_hash"
        val entry1 = CatalogEntryEntity(hash, "pub1", 1L)
        val frag1 = FragmentLocationEntity(0, hash, 0, "hash1", "[]")
        
        withContext(Dispatchers.IO) { dao.insertWithFragments(entry1, listOf(frag1)) }

        val frag2 = FragmentLocationEntity(0, hash, 0, "hash2", "[]")
        val frag3 = FragmentLocationEntity(0, hash, 1, "hash3", "[]")
        // Updating entry but with 2 fragments now instead of 1
        withContext(Dispatchers.IO) { dao.insertWithFragments(entry1, listOf(frag2, frag3)) }

        val retrieved = withContext(Dispatchers.IO) { dao.getCatalogEntry(hash) }
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.fragmentLocations?.size)
    }

    @Test
    fun testGetNonExistentEntry() = runBlocking {
        val retrieved = withContext(Dispatchers.IO) { dao.getCatalogEntry("missing_hash") }
        assertNull(retrieved)
    }
}
