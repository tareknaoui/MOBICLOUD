package com.mobicloud.data.repository_impl

import com.mobicloud.core.security.KeystoreManager
import com.mobicloud.data.local.dao.IdentityDao
import com.mobicloud.data.local.entity.NodeIdentityEntity
import com.mobicloud.domain.models.NodeIdentity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IdentityRepositoryImplTest {

    private lateinit var keystoreManager: KeystoreManager
    private lateinit var identityDao: IdentityDao
    private lateinit var repository: IdentityRepositoryImpl

    private val testNodeId = "a1b2c3d4e5f6a7b8"
    private val testPublicKeyBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val testIdentity = NodeIdentity(
        nodeId = testNodeId,
        publicKeyBytes = testPublicKeyBytes,
        reliabilityScore = 1.0f
    )
    private val testEntity = NodeIdentityEntity(
        nodeId = testNodeId,
        publicKeyBytes = testPublicKeyBytes,
        reliabilityScore = 1.0f
    )

    @Before
    fun setUp() {
        keystoreManager = mockk()
        identityDao = mockk(relaxed = true)
        repository = IdentityRepositoryImpl(keystoreManager, identityDao)
    }

    // --- AC4: Au prochain démarrage, la clé existante est réutilisée (pas de régénération) ---

    @Test
    fun `getIdentity returns existing identity from DB without calling keystore`() = runTest {
        // Arrange: DB has an existing identity
        coEvery { identityDao.getIdentity() } returns testEntity

        // Act
        val result = repository.getIdentity()

        // Assert
        assertTrue(result.isSuccess)
        val identity = result.getOrNull()!!
        assertEquals(testNodeId, identity.nodeId)
        assertEquals(testPublicKeyBytes.toList(), identity.publicKeyBytes.toList())
        assertEquals(1.0f, identity.reliabilityScore)

        // Keystore must NOT be called when DB has the identity
        coVerify(exactly = 0) { keystoreManager.getExistingIdentity() }
        coVerify(exactly = 0) { keystoreManager.generateIdentity() }
    }

    // --- AC1 & AC3: Première utilisation, génération + dérivation du nodeId ---

    @Test
    fun `getIdentity generates and persists new identity on first launch`() = runTest {
        // Arrange: DB is empty, keystore is also empty, so we generate a new one
        coEvery { identityDao.getIdentity() } returns null
        every { keystoreManager.getExistingIdentity() } returns null
        every { keystoreManager.generateIdentity() } returns testIdentity

        val entitySlot = slot<NodeIdentityEntity>()
        coEvery { identityDao.insertIdentity(capture(entitySlot)) } returns Unit

        // Act
        val result = repository.getIdentity()

        // Assert
        assertTrue(result.isSuccess)
        val identity = result.getOrNull()!!
        assertEquals(testNodeId, identity.nodeId)

        // Verify identity was persisted in DB
        coVerify(exactly = 1) { identityDao.insertIdentity(any()) }
        assertEquals(testNodeId, entitySlot.captured.nodeId)
    }

    @Test
    fun `getIdentity restores from keystore and persists if DB is empty but keystore has identity`() = runTest {
        // Arrange: DB empty, but keystore already has an entry (post-app reinstall edge case)
        coEvery { identityDao.getIdentity() } returns null
        every { keystoreManager.getExistingIdentity() } returns testIdentity

        // Act
        val result = repository.getIdentity()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(testNodeId, result.getOrNull()!!.nodeId)

        // Should persist to DB
        coVerify(exactly = 1) { identityDao.insertIdentity(any()) }
        // Should NOT generate a new identity
        coVerify(exactly = 0) { keystoreManager.generateIdentity() }
    }

    // --- AC: Gestion d'erreur sans exception silencieuse ---

    @Test
    fun `getIdentity returns Result failure if keystore generation fails`() = runTest {
        // Arrange: DB and keystore both empty, generation throws
        coEvery { identityDao.getIdentity() } returns null
        every { keystoreManager.getExistingIdentity() } returns null
        every { keystoreManager.generateIdentity() } throws RuntimeException("Keystore hardware failure")

        // Act
        val result = repository.getIdentity()

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Keystore hardware failure", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getIdentity returns Result failure if DAO throws`() = runTest {
        // Arrange: DB query fails
        coEvery { identityDao.getIdentity() } throws RuntimeException("Database I/O error")

        // Act
        val result = repository.getIdentity()

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }
}
