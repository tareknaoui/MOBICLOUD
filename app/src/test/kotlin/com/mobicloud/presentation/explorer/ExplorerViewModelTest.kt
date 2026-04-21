package com.mobicloud.presentation.explorer

import android.content.Context
import com.mobicloud.core.security.FragmentCipherUseCase
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import com.mobicloud.presentation.explorer.components.AvailabilityState
import com.mobicloud.presentation.explorer.components.availabilityState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var context: Context
    private val catalogFlow = MutableStateFlow<List<CatalogEntry>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        catalogRepository = mockk()
        gossipSyncUseCase = mockk(relaxed = true)
        encodeErasureFragmentsUseCase = mockk(relaxed = true)
        fragmentCipherUseCase = mockk(relaxed = true)
        distributeEncryptedBlocksUseCase = mockk(relaxed = true)
        securityRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { catalogRepository.getAllEntriesFlow() } returns catalogFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ExplorerViewModel(
        catalogRepository = catalogRepository,
        gossipSyncUseCase = gossipSyncUseCase,
        encodeErasureFragmentsUseCase = encodeErasureFragmentsUseCase,
        fragmentCipherUseCase = fragmentCipherUseCase,
        distributeEncryptedBlocksUseCase = distributeEncryptedBlocksUseCase,
        securityRepository = securityRepository,
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

    private fun makeCatalogEntry(
        fileHash: String = "aabbccdd1234567890abcdef1234567890abcdef1234567890abcdef12345678",
        fragmentLocations: List<FragmentLocation> = emptyList()
    ) = CatalogEntry(
        fileHash = fileHash,
        ownerPubKeyHash = "ownerHash01234567",
        versionClock = System.currentTimeMillis(),
        fragmentLocations = fragmentLocations
    )
}
