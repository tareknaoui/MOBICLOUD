package com.mobicloud.presentation.explorer

import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.core.security.FragmentCipherUseCase
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.LocalizeFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.SelectOptimalPeersUseCase
import android.content.Context
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerViewModelTrashTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var viewModel: ExplorerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        catalogRepository = mockk(relaxed = true) {
            every { getActiveEntriesFlow() } returns flowOf(emptyList())
        }
        viewModel = ExplorerViewModel(
            catalogRepository = catalogRepository,
            gossipSyncUseCase = mockk(relaxed = true),
            encodeErasureFragmentsUseCase = mockk(relaxed = true),
            fragmentCipherUseCase = mockk(relaxed = true),
            distributeEncryptedBlocksUseCase = mockk(relaxed = true),
            securityRepository = mockk(relaxed = true),
            localizeFileBlocksUseCase = mockk(relaxed = true),
            downloadFileBlocksUseCase = mockk(relaxed = true),
            assembleDownloadedFileUseCase = mockk(relaxed = true),
            selectOptimalPeersUseCase = mockk(relaxed = true),
            context = mockk<Context>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `moveToTrash appelle moveToTrash du repository`() = runTest {
        viewModel.moveToTrash("hash-abc")
        advanceUntilIdle()
        coVerify(exactly = 1) { catalogRepository.moveToTrash("hash-abc") }
    }

    @Test
    fun `moveToTrash emet un undoEvent avec le fileHash`() = runTest {
        viewModel.moveToTrash("hash-abc")
        advanceUntilIdle()
        val emitted = viewModel.undoEvent.first()
        assertEquals("hash-abc", emitted)
    }

    @Test
    fun `undoMoveToTrash appelle restoreFromTrash du repository`() = runTest {
        viewModel.undoMoveToTrash("hash-abc")
        advanceUntilIdle()
        coVerify(exactly = 1) { catalogRepository.restoreFromTrash("hash-abc") }
    }
}
