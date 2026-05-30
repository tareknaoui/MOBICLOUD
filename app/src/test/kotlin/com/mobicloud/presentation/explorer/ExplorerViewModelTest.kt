package com.mobicloud.presentation.explorer

import android.content.Context
import android.util.Log
import com.mobicloud.core.security.FragmentCipherUseCase
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.LocalizeFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadProgressState
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.OptimalPeersResult
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.SelectOptimalPeersUseCase
import com.mobicloud.domain.models.ErasureParameters
import io.mockk.coEvery
import com.mobicloud.presentation.explorer.components.AvailabilityState
import com.mobicloud.presentation.explorer.components.availabilityState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var catalogRepository: CatalogRepository
    private lateinit var gossipSyncUseCase: GossipSyncUseCase
    private lateinit var encodeErasureFragmentsUseCase: EncodeErasureFragmentsUseCase
    private lateinit var fragmentCipherUseCase: FragmentCipherUseCase
    private lateinit var distributeEncryptedBlocksUseCase: DistributeEncryptedBlocksUseCase
    private lateinit var securityRepository: SecurityRepository
    private lateinit var localizeFileBlocksUseCase: LocalizeFileBlocksUseCase
    private lateinit var downloadFileBlocksUseCase: com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
    private lateinit var assembleDownloadedFileUseCase: com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
    private lateinit var selectOptimalPeersUseCase: SelectOptimalPeersUseCase
    private lateinit var context: Context
    private val catalogFlow = MutableStateFlow<List<CatalogEntry>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        catalogRepository = mockk()
        gossipSyncUseCase = mockk(relaxed = true)
        encodeErasureFragmentsUseCase = mockk(relaxed = true)
        fragmentCipherUseCase = mockk(relaxed = true)
        distributeEncryptedBlocksUseCase = mockk(relaxed = true)
        securityRepository = mockk(relaxed = true)
        localizeFileBlocksUseCase = mockk(relaxed = true)
        downloadFileBlocksUseCase = mockk(relaxed = true)
        assembleDownloadedFileUseCase = mockk(relaxed = true)
        selectOptimalPeersUseCase = mockk()
        coEvery { selectOptimalPeersUseCase(any(), any(), any()) } returns Result.success(
            OptimalPeersResult(params = ErasureParameters(), selectedPeers = emptyList())
        )
        coEvery { catalogRepository.getEntry(any()) } returns Result.success(null)
        coEvery { catalogRepository.purgeExpired() } returns Result.success(Unit)
        context = mockk(relaxed = true)
        every { catalogRepository.getActiveEntriesFlow() } returns catalogFlow
        every { catalogRepository.getActiveFolderNamesFlow() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    private fun createViewModel() = ExplorerViewModel(
        catalogRepository = catalogRepository,
        gossipSyncUseCase = gossipSyncUseCase,
        encodeErasureFragmentsUseCase = encodeErasureFragmentsUseCase,
        fragmentCipherUseCase = fragmentCipherUseCase,
        distributeEncryptedBlocksUseCase = distributeEncryptedBlocksUseCase,
        securityRepository = securityRepository,
        localizeFileBlocksUseCase = localizeFileBlocksUseCase,
        downloadFileBlocksUseCase = downloadFileBlocksUseCase,
        assembleDownloadedFileUseCase = assembleDownloadedFileUseCase,
        selectOptimalPeersUseCase = selectOptimalPeersUseCase,
        context = context
    )

    // Test 1 — État vide initial
    @Test
    fun `catalogEntries expose liste vide par défaut`() = runTest {
        val viewModel = createViewModel()
        val collected = mutableListOf<List<CatalogEntry>>()
        val job = launch { viewModel.catalogEntries.collect { collected.add(it) } }
        advanceUntilIdle()
        job.cancel()

        assert(collected.isNotEmpty())
        assertEquals(emptyList<CatalogEntry>(), collected.first())
    }

    // Test 2 — Catalogue non vide
    @Test
    fun `catalogEntries reflète les entrées du repository`() = runTest {
        val entries = listOf(
            makeCatalogEntry("aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678"),
            makeCatalogEntry("bbccddee1234567890abcdef1234567890abcdef1234567890abcdef12345678")
        )
        catalogFlow.value = entries

        val viewModel = createViewModel()
        val collected = mutableListOf<List<CatalogEntry>>()
        val job = launch { viewModel.catalogEntries.collect { collected.add(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(2, collected.last().size)
    }

    // Test 3 — Pull-to-refresh appelle runGossipCycle()
    @Test
    fun `refreshCatalog appelle runGossipCycle exactement une fois`() = runTest {
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.refreshCatalog()
        advanceUntilIdle()

        coVerify(exactly = 1) { gossipSyncUseCase.runGossipCycle() }
    }

    // Test 3 — isRefreshing revient à false après refresh
    @Test
    fun `isRefreshing revient à false après refreshCatalog`() = runTest {
        coEvery { gossipSyncUseCase.runGossipCycle() } returns Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.refreshCatalog()
        advanceUntilIdle()

        assertFalse(viewModel.isRefreshing.value)
    }

    // Test 4 — isRefreshing false par défaut
    @Test
    fun `isRefreshing est false par défaut`() = runTest {
        val viewModel = createViewModel()
        assertFalse(viewModel.isRefreshing.value)
    }

    // Test 5 — availabilityState() : Complet
    @Test
    fun `availabilityState retourne COMPLET quand tous les fragments ont des nodeIds`() {
        val entry = makeCatalogEntry(
            fragmentLocations = listOf(
                FragmentLocation(0, "hash0", listOf("node1", "node2")),
                FragmentLocation(1, "hash1", listOf("node3"))
            )
        )
        assertEquals(AvailabilityState.COMPLET, entry.availabilityState())
    }

    // Test 6 — availabilityState() : Partiel
    @Test
    fun `availabilityState retourne PARTIEL quand certains fragments ont des nodeIds vides`() {
        val entry = makeCatalogEntry(
            fragmentLocations = listOf(
                FragmentLocation(0, "hash0", listOf("node1")),
                FragmentLocation(1, "hash1", emptyList())
            )
        )
        assertEquals(AvailabilityState.PARTIEL, entry.availabilityState())
    }

    // Test 7 — availabilityState() : Dégradé (tous vides)
    @Test
    fun `availabilityState retourne DEGRADE quand tous les nodeIds sont vides`() {
        val entry = makeCatalogEntry(
            fragmentLocations = listOf(
                FragmentLocation(0, "hash0", emptyList()),
                FragmentLocation(1, "hash1", emptyList())
            )
        )
        assertEquals(AvailabilityState.DEGRADE, entry.availabilityState())
    }

    // Test 7 — availabilityState() : Dégradé (liste vide)
    @Test
    fun `availabilityState retourne DEGRADE quand fragmentLocations est vide`() {
        val entry = makeCatalogEntry(fragmentLocations = emptyList())
        assertEquals(AvailabilityState.DEGRADE, entry.availabilityState())
    }

    // --- Story 6.4 Tests ---

    // Test Story 6.4 #1 — contributions tracking : DownloadState.Downloading.contributions
    @Test
    fun `startDownload propage contributions depuis DownloadProgressState Progress`() = runTest {
        val fileHash = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        val blockMap = emptyMap<String, ResolvedBlockLocation>()
        val contrib1 = DownloadProgressState.BlockContribution("nodeABC123", 0, 42L)
        val contrib2 = DownloadProgressState.BlockContribution("nodeDEF456", 1, 85L)

        coEvery { localizeFileBlocksUseCase.invoke(fileHash) } returns Result.success(blockMap)
        every { downloadFileBlocksUseCase.invoke(any(), any()) } returns flowOf(
            DownloadProgressState.Progress(
                received = 2,
                k = 4,
                failed = 0,
                contributions = listOf(contrib1, contrib2)
            )
        )
        every { assembleDownloadedFileUseCase.invoke(any(), any()) } returns flow { }

        val viewModel = createViewModel()
        viewModel.initiateDownload(fileHash)
        advanceUntilIdle()

        val state = viewModel.downloadState.value
        assertTrue("expected Downloading, got $state", state is DownloadState.Downloading)
        state as DownloadState.Downloading
        assertEquals(2, state.contributions.size)
    }

    // Test Story 6.4 #2 — slowNodeIds propagés
    @Test
    fun `startDownload propage slowNodeIds depuis DownloadProgressState Progress`() = runTest {
        val fileHash = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        val blockMap = emptyMap<String, ResolvedBlockLocation>()

        coEvery { localizeFileBlocksUseCase.invoke(fileHash) } returns Result.success(blockMap)
        every { downloadFileBlocksUseCase.invoke(any(), any()) } returns flowOf(
            DownloadProgressState.Progress(
                received = 1,
                k = 4,
                failed = 0,
                slowNodeIds = setOf("abc123")
            )
        )
        every { assembleDownloadedFileUseCase.invoke(any(), any()) } returns flow { }

        val viewModel = createViewModel()
        viewModel.initiateDownload(fileHash)
        advanceUntilIdle()

        val state = viewModel.downloadState.value
        assertTrue(state is DownloadState.Downloading)
        state as DownloadState.Downloading
        assertTrue(state.slowNodeIds.contains("abc123"))
    }

    // Test Story 6.4 #3 — resetDownloadState remet Idle
    @Test
    fun `resetDownloadState remet downloadState à Idle`() = runTest {
        val fileHash = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        val blockMap = emptyMap<String, ResolvedBlockLocation>()

        coEvery { localizeFileBlocksUseCase.invoke(fileHash) } returns Result.success(blockMap)
        every { downloadFileBlocksUseCase.invoke(any(), any()) } returns flowOf(
            DownloadProgressState.Progress(received = 1, k = 4, failed = 0)
        )
        every { assembleDownloadedFileUseCase.invoke(any(), any()) } returns flow { }

        val viewModel = createViewModel()
        viewModel.initiateDownload(fileHash)
        advanceUntilIdle()

        viewModel.resetDownloadState()
        advanceUntilIdle()

        assertEquals(DownloadState.Idle, viewModel.downloadState.value)
    }

    // Test Story 6.4 #4 — Assembled contient durationMs > 0
    @Test
    fun `startDownload produit Assembled avec durationMs positif`() = runTest {
        val fileHash = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        val blockMap = emptyMap<String, ResolvedBlockLocation>()

        coEvery { localizeFileBlocksUseCase.invoke(fileHash) } returns Result.success(blockMap)
        every { downloadFileBlocksUseCase.invoke(any(), any()) } returns flowOf(
            DownloadProgressState.Completed(emptyMap())
        )
        every { assembleDownloadedFileUseCase.invoke(any(), any()) } returns flowOf(
            AssembleDownloadedFileUseCase.AssembleProgress.Finalized(
                AssembleDownloadedFileUseCase.AssembleResult.Success("/sdcard/test.txt")
            )
        )

        val viewModel = createViewModel()
        viewModel.initiateDownload(fileHash)
        advanceUntilIdle()

        val state = viewModel.downloadState.value
        assertTrue("expected Assembled, got $state", state is DownloadState.Assembled)
        state as DownloadState.Assembled
        assertTrue("durationMs should be >= 0, got ${state.durationMs}", state.durationMs >= 0L)
    }

    // Test Story Preview #1 — initiateDownload avec isPreview = true
    @Test
    fun `initiateDownload avec isPreview true produit Locating et Located avec isPreview true`() = runTest {
        val fileHash = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        val blockMap = emptyMap<String, ResolvedBlockLocation>()

        coEvery { localizeFileBlocksUseCase.invoke(fileHash) } returns Result.success(blockMap)
        every { downloadFileBlocksUseCase.invoke(any(), any()) } returns flowOf(
            DownloadProgressState.Progress(received = 1, k = 4, failed = 0)
        )
        every { assembleDownloadedFileUseCase.invoke(any(), any(), any()) } returns flow { }

        val viewModel = createViewModel()
        viewModel.initiateDownload(fileHash, isPreview = true)
        advanceUntilIdle()

        val state = viewModel.downloadState.value
        assertTrue("expected Downloading, got $state", state is DownloadState.Downloading)
        state as DownloadState.Downloading
        assertTrue(state.isPreview)
    }

    // Test Story Preview #2 — startDownload avec isPreview = true appelle assembleDownloadedFileUseCase avec isPreview = true
    @Test
    fun `startDownload avec isPreview true appelle assembleDownloadedFileUseCase avec isPreview true et produit Assembled avec isPreview true`() = runTest {
        val fileHash = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678"
        val blockMap = emptyMap<String, ResolvedBlockLocation>()

        coEvery { localizeFileBlocksUseCase.invoke(fileHash) } returns Result.success(blockMap)
        every { downloadFileBlocksUseCase.invoke(any(), any()) } returns flowOf(
            DownloadProgressState.Completed(emptyMap())
        )
        every { assembleDownloadedFileUseCase.invoke(any(), any(), isPreview = true) } returns flowOf(
            AssembleDownloadedFileUseCase.AssembleProgress.Finalized(
                AssembleDownloadedFileUseCase.AssembleResult.Success("/sdcard/test.txt")
            )
        )

        val viewModel = createViewModel()
        viewModel.initiateDownload(fileHash, isPreview = true)
        advanceUntilIdle()

        val state = viewModel.downloadState.value
        assertTrue("expected Assembled, got $state", state is DownloadState.Assembled)
        state as DownloadState.Assembled
        assertTrue(state.isPreview)
        coVerify(exactly = 1) { assembleDownloadedFileUseCase.invoke(fileHash, any(), isPreview = true) }
    }

    // Test Story Search & Filter #1 — setSearchQuery
    @Test
    fun `setSearchQuery filtre les catalogEntries par nom de fichier`() = runTest {
        val entries = listOf(
            makeCatalogEntry(fileHash = "hash1", originalFileName = "vacation_photo.jpg"),
            makeCatalogEntry(fileHash = "hash2", originalFileName = "monthly_report.pdf"),
            makeCatalogEntry(fileHash = "hash3", originalFileName = "sunset_video.mp4")
        )
        catalogFlow.value = entries

        val viewModel = createViewModel()
        val collected = mutableListOf<List<CatalogEntry>>()
        val job = launch { viewModel.catalogEntries.collect { collected.add(it) } }
        advanceUntilIdle()

        viewModel.setSearchQuery("report")
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, collected.last().size)
        assertEquals("monthly_report.pdf", collected.last().first().originalFileName)
    }

    // Test Story Search & Filter #2 — setSelectedCategory
    @Test
    fun `setSelectedCategory filtre les catalogEntries par categorie`() = runTest {
        val entries = listOf(
            makeCatalogEntry(fileHash = "hash1", originalFileName = "vacation_photo.jpg"),
            makeCatalogEntry(fileHash = "hash2", originalFileName = "monthly_report.pdf"),
            makeCatalogEntry(fileHash = "hash3", originalFileName = "sunset_video.mp4")
        )
        catalogFlow.value = entries

        val viewModel = createViewModel()
        val collected = mutableListOf<List<CatalogEntry>>()
        val job = launch { viewModel.catalogEntries.collect { collected.add(it) } }
        advanceUntilIdle()

        viewModel.setSelectedCategory("Videos")
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, collected.last().size)
        assertEquals("sunset_video.mp4", collected.last().first().originalFileName)
    }

    private fun makeCatalogEntry(
        fileHash: String = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678",
        fragmentLocations: List<FragmentLocation> = emptyList(),
        originalFileName: String = "",
        originalFileSize: Long = 0L
    ) = CatalogEntry(
        fileHash = fileHash,
        ownerPubKeyHash = "ownerHash01234567",
        versionClock = System.currentTimeMillis(),
        fragmentLocations = fragmentLocations,
        originalFileName = originalFileName,
        originalFileSize = originalFileSize
    )
}
