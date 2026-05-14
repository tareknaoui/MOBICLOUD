package com.mobicloud.presentation.explorer

import android.content.Context
import com.mobicloud.core.security.FragmentCipherUseCase
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.LocalizeFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.SelectOptimalPeersUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerViewModelCancelUploadTest {

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
    fun `cancelUpload passe le state a Cancelled`() = runTest {
        // On force manuellement l'état InProgress (storeFile nécessiterait un vrai Uri)
        // On teste directement cancelUpload depuis Idle — state doit être Cancelled immédiatement
        viewModel.cancelUpload()
        advanceUntilIdle()
        assertTrue(viewModel.storeState.value is StoreState.Cancelled)
    }

    @Test
    fun `cancelUpload scheduleReset repasse a Idle apres 3s`() = runTest {
        viewModel.cancelUpload()
        advanceUntilIdle()
        assertEquals(StoreState.Cancelled, viewModel.storeState.value)
        advanceTimeBy(3001L)
        advanceUntilIdle()
        assertEquals(StoreState.Idle, viewModel.storeState.value)
    }

    @Test
    fun `uploadBusyEvent emet quand storeFile est appele pendant InProgress`() = runTest {
        // Forcer _storeState à InProgress via reflection
        val field = ExplorerViewModel::class.java.getDeclaredField("_storeState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<StoreState>
        flow.value = StoreState.InProgress.Encoding

        val mockUri = mockk<android.net.Uri>(relaxed = true)
        viewModel.storeFile(mockUri)
        advanceUntilIdle()

        val event = viewModel.uploadBusyEvent.first()
        assertEquals(Unit, event)
    }
}
