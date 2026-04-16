package com.mobicloud.data.repository

import com.mobicloud.domain.models.NetworkLogEvent
import com.mobicloud.domain.models.NetworkType
import com.mobicloud.domain.models.NodeDiagnostics
import com.mobicloud.domain.models.ServiceStatus
import com.mobicloud.domain.repository.DiagnosticsRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.NetworkServiceController
import com.mobicloud.presentation.dashboard.DashboardViewModel
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

    private val serviceStatusFlow = MutableStateFlow(ServiceStatus.STOPPED)
    private val diagnosticsFlow = MutableStateFlow(NodeDiagnostics.DEFAULT)
    private val eventsFlow = MutableStateFlow<List<NetworkLogEvent>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        networkServiceController = mockk()
        diagnosticsRepository = mockk()
        networkEventRepository = mockk()

        every { networkServiceController.serviceStatus } returns serviceStatusFlow
        every { diagnosticsRepository.diagnostics } returns diagnosticsFlow
        every { networkEventRepository.events } returns eventsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DashboardViewModel(
        networkServiceController,
        diagnosticsRepository,
        networkEventRepository
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
