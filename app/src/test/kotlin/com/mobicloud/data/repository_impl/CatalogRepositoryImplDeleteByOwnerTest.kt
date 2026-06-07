package com.mobicloud.data.repository_impl

import com.mobicloud.data.local.dao.CatalogDao
import com.mobicloud.domain.usecase.m05_dht_catalog.CalculateDhtRangeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Couvre [CatalogRepositoryImpl.deleteAllByOwner] : délégation au DAO + propagation d'erreur.
 */
class CatalogRepositoryImplDeleteByOwnerTest {

    private lateinit var catalogDao: CatalogDao
    private lateinit var repository: CatalogRepositoryImpl

    private val ownerHash = "owner-pub-key-hash-abc"

    @Before
    fun setUp() {
        catalogDao = mockk(relaxed = true)
        val calculateDhtRange = mockk<CalculateDhtRangeUseCase>(relaxed = true)
        repository = CatalogRepositoryImpl(catalogDao, calculateDhtRange)
    }

    @Test
    fun `deleteAllByOwner delegates to DAO and succeeds`() = runTest {
        val result = repository.deleteAllByOwner(ownerHash)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { catalogDao.deleteAllEntriesByOwner(ownerHash) }
    }

    @Test
    fun `deleteAllByOwner propagates DAO failure as Result failure`() = runTest {
        coEvery { catalogDao.deleteAllEntriesByOwner(any()) } throws RuntimeException("DB locked")

        val result = repository.deleteAllByOwner(ownerHash)

        assertTrue(result.isFailure)
    }
}
