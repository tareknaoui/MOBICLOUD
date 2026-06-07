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
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
            relayRepository = mockk(relaxed = true),
            context = mockk<Context>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun injectActiveFakeJob() {
        val storeJobField = ExplorerViewModel::class.java.getDeclaredField("storeJob")
        storeJobField.isAccessible = true
        val fakeJob = kotlinx.coroutines.GlobalScope.launch { kotlinx.coroutines.delay(60_000L) }
        storeJobField.set(viewModel, fakeJob)
    }

    @Test
    fun `cancelUpload passe le state a Cancelled`() = runTest {
        injectActiveFakeJob()
        viewModel.cancelUpload()
        // _storeState.value est assigné de façon synchrone dans cancelUpload()
        assertTrue(viewModel.storeState.value is StoreState.Cancelled)
    }

    @Test
    fun `cancelUpload scheduleReset repasse a Idle apres 3s`() = runTest {
        injectActiveFakeJob()
        viewModel.cancelUpload()
        assertEquals(StoreState.Cancelled, viewModel.storeState.value)
        advanceTimeBy(3001L)
        runCurrent()
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

        // Démarrer la collection AVANT storeFile : SharedFlow replay=0, l'event serait perdu sinon.
        val eventDeferred = async { viewModel.uploadBusyEvent.first() }
        advanceUntilIdle()  // laisser le collector s'inscrire

        val mockUri = mockk<android.net.Uri>(relaxed = true)
        viewModel.storeFile(mockUri)
        advanceUntilIdle()

        val event = eventDeferred.await()
        assertEquals(Unit, event)
    }

    // [Review][Patch] P1 — guard Idle : cancelUpload() depuis Idle doit être un no-op.
    @Test
    fun `cancelUpload depuis Idle est un no-op - state reste Idle`() = runTest {
        assertEquals(StoreState.Idle, viewModel.storeState.value)
        viewModel.cancelUpload()
        advanceUntilIdle()
        assertEquals(StoreState.Idle, viewModel.storeState.value)
    }

    // [Review][Patch] P2 — guard double-tap : 2e appel depuis Cancelled ne réinitialise pas le resetJob.
    @Test
    fun `cancelUpload double-tap ne bloque pas le retour a Idle`() = runTest {
        injectActiveFakeJob()

        viewModel.cancelUpload()             // 1er appel → passe à Cancelled, lance resetJob(3s)
        assertEquals(StoreState.Cancelled, viewModel.storeState.value)

        viewModel.cancelUpload()             // 2e appel → guard P2 : retour immédiat, resetJob intact
        assertEquals(StoreState.Cancelled, viewModel.storeState.value)

        advanceTimeBy(3001L)
        runCurrent()
        assertEquals(StoreState.Idle, viewModel.storeState.value)
    }
}
