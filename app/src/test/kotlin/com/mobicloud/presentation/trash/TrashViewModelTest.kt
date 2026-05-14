package com.mobicloud.presentation.trash

import com.mobicloud.domain.repository.CatalogRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var viewModel: TrashViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        catalogRepository = mockk(relaxed = true) {
            every { getDeletedEntriesFlow() } returns flowOf(emptyList())
        }
        viewModel = TrashViewModel(catalogRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `restoreEntry appelle restoreFromTrash`() = runTest {
        viewModel.restoreEntry("hash-abc")
        advanceUntilIdle()
        coVerify(exactly = 1) { catalogRepository.restoreFromTrash("hash-abc") }
    }

    @Test
    fun `permanentlyDelete appelle permanentlyDelete du repository`() = runTest {
        viewModel.permanentlyDelete("hash-xyz")
        advanceUntilIdle()
        coVerify(exactly = 1) { catalogRepository.permanentlyDelete("hash-xyz") }
    }

    @Test
    fun `emptyTrash appelle emptyTrash du repository`() = runTest {
        viewModel.emptyTrash()
        advanceUntilIdle()
        coVerify(exactly = 1) { catalogRepository.emptyTrash() }
    }
}
