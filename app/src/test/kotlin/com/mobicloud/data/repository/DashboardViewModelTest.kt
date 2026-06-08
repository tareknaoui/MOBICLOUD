package com.mobicloud.data.repository

import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.models.NetworkType
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.models.TransferChannelState
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.HostedBlockRepository
import com.mobicloud.domain.repository.IdentityRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NetworkServiceController
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.usecase.m06_m07_repair_migration.CircuitBreakerUseCase
import com.mobicloud.domain.usecase.m11_join.MemberSnapshotCacheUseCase
import com.mobicloud.presentation.dashboard.DashboardViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var networkServiceController: NetworkServiceController
    private lateinit var diagnosticsRepository: DiagnosticsRepository
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var peerRepository: PeerRepository
    private lateinit var identityRepository: IdentityRepository
    private lateinit var circuitBreakerUseCase: CircuitBreakerUseCase
    private lateinit var nodeSettingsRepository: NodeSettingsRepository
    private lateinit var hostedBlockRepository: HostedBlockRepository
    private lateinit var memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase

    private val serviceStatusFlow = MutableStateFlow(ServiceStatus.STOPPED)
    private val diagnosticsFlow = MutableStateFlow(NodeDiagnostics.DEFAULT)
    private val eventsFlow = MutableStateFlow<List<NetworkLogEvent>>(emptyList())
    private val peersFlow = MutableStateFlow<List<Peer>>(emptyList())
    private val transferChannelStateFlow = MutableStateFlow(TransferChannelState.DIRECT)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        networkServiceController = mockk()
        diagnosticsRepository = mockk()
        networkEventRepository = mockk()
        peerRepository = mockk()
        identityRepository = mockk()
        circuitBreakerUseCase = mockk(relaxed = true)
        nodeSettingsRepository = mockk(relaxed = true)
        hostedBlockRepository = mockk(relaxed = true)
        memberSnapshotCacheUseCase = mockk(relaxed = true)

        every { networkServiceController.serviceStatus } returns serviceStatusFlow
        every { diagnosticsRepository.diagnostics } returns diagnosticsFlow
        every { networkEventRepository.events } returns eventsFlow
        every { peerRepository.peers } returns peersFlow
        coEvery { identityRepository.getIdentity() } returns Result.success(
            NodeIdentity("testNode", ByteArray(0), 0.8f)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // [Bug 2 fix 2026-06-08] peerRepository retiré du constructeur — nodeRole lit désormais
    // memberSnapshotCacheUseCase.inMemory (annuaire de membres) au lieu de peerRepository.peers.
    private fun createViewModel() = DashboardViewModel(
        networkServiceController,
        diagnosticsRepository,
        networkEventRepository,
        identityRepository,
        circuitBreakerUseCase,
        nodeSettingsRepository,
        hostedBlockRepository,
        memberSnapshotCacheUseCase,
        transferChannelStateFlow
    )

    @Test
    fun `diagnostics expose la valeur initiale DEFAULT`() = runTest {
        val viewModel = createViewModel()
        val collected = mutableListOf<NodeDiagnostics>()
        val job = launch { viewModel.diagnostics.collect { collected.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertEquals(NodeDiagnostics.DEFAULT, collected.first())
    }

    @Test
    fun `networkEvents expose une liste vide par défaut`() = runTest {
        val viewModel = createViewModel()
        val collected = mutableListOf<List<NetworkLogEvent>>()
        val job = launch { viewModel.networkEvents.collect { collected.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertTrue(collected.first().isEmpty())
    }

    @Test
    fun `hasActivePeers retourne false quand activePeerCount est 0`() = runTest {
        val viewModel = createViewModel()
        val collected = mutableListOf<Boolean>()
        val job = launch { viewModel.hasActivePeers.collect { collected.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertFalse(collected.last())
    }

    @Test
    fun `hasActivePeers retourne true quand activePeerCount est positif`() = runTest {
        diagnosticsFlow.value = NodeDiagnostics(
            batteryPercent = 80,
            uptimeMs = 1000L,
            networkType = NetworkType.WIFI,
            activePeerCount = 3,
            reliabilityScore = 0.75f
        )
        val viewModel = createViewModel()
        val collected = mutableListOf<Boolean>()
        val job = launch { viewModel.hasActivePeers.collect { collected.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertTrue(collected.last())
    }

    @Test
    fun `serviceStatus expose la valeur initiale STOPPED par défaut`() = runTest {
        val viewModel = createViewModel()
        val collected = mutableListOf<ServiceStatus>()
        val job = launch { viewModel.serviceStatus.collect { collected.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertEquals(ServiceStatus.STOPPED, collected.first())
    }

    @Test
    fun `networkEvents reflète les événements du repository`() = runTest {
        val events = listOf(
            NetworkLogEvent(System.currentTimeMillis(), "[UDP] Heartbeat reçu de ABCDEFGH"),
            NetworkLogEvent(System.currentTimeMillis() - 1000, "[TCP] Connexion avec 12345678")
        )
        eventsFlow.value = events

        val viewModel = createViewModel()
        val collected = mutableListOf<List<NetworkLogEvent>>()
        val job = launch { viewModel.networkEvents.collect { collected.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isNotEmpty())
        assertEquals(events, collected.last())
    }
}
