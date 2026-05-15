package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.DiscoverySource
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CircuitBreakerUseCaseTest {

    private lateinit var peerRepository: PeerRepository
    private lateinit var networkEventRepository: NetworkEventRepository
    private lateinit var peersFlow: MutableStateFlow<List<Peer>>

    @Before
    fun setup() {
        peerRepository = mockk()
        networkEventRepository = mockk()
        peersFlow = MutableStateFlow(emptyList())

        every { peerRepository.peers } returns peersFlow
        every { networkEventRepository.pushEvent(any()) } just Runs
    }

    private fun createPeers(count: Int, active: Boolean): List<Peer> {
        return (1..count).map { i ->
            Peer(
                identity = NodeIdentity("node$i", ByteArray(0)),
                lastSeenTimestampMs = 1000L,
                isActive = active
            )
        }
    }

    @Test
    fun `does not activate circuit breaker when churn is under 30%`() = runTest {
        val useCase = CircuitBreakerUseCase(
            peerRepository = peerRepository,
            networkEventRepository = networkEventRepository,
            applicationScope = backgroundScope
        )
        useCase.currentTimeProvider = { testScheduler.currentTime }
        useCase.startupTimeMs = -CircuitBreakerUseCase.STARTUP_GRACE_MS - 1L

        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        val updatedPeers = initialPeers.mapIndexed { index, peer ->
            if (index < 2) peer.copy(isActive = false) else peer
        }
        peersFlow.value = updatedPeers
        advanceTimeBy(1000)

        assertFalse(useCase.isCircuitOpen.value)
    }

    /**
     * P8 — Test à exactement 30% : "plus de 30%" signifie > 0.30, donc 3/10 = 30% exact NE doit PAS déclencher.
     */
    @Test
    fun `does not activate circuit breaker at exactly 30% churn boundary`() = runTest {
        val useCase = CircuitBreakerUseCase(
            peerRepository = peerRepository,
            networkEventRepository = networkEventRepository,
            applicationScope = backgroundScope
        )
        useCase.currentTimeProvider = { testScheduler.currentTime }
        useCase.startupTimeMs = -CircuitBreakerUseCase.STARTUP_GRACE_MS - 1L

        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        val updatedPeers = initialPeers.mapIndexed { index, peer ->
            if (index < 3) peer.copy(isActive = false) else peer
        }
        peersFlow.value = updatedPeers
        advanceTimeBy(1000)

        assertFalse("30% exact ne doit pas activer le circuit (condition est > 0.3)", useCase.isCircuitOpen.value)
    }

    @Test
    fun `activates circuit breaker when churn exceeds 30%`() = runTest {
        val useCase = CircuitBreakerUseCase(
            peerRepository = peerRepository,
            networkEventRepository = networkEventRepository,
            applicationScope = backgroundScope
        )
        useCase.currentTimeProvider = { testScheduler.currentTime }
        useCase.startupTimeMs = -CircuitBreakerUseCase.STARTUP_GRACE_MS - 1L

        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        val updatedPeers = initialPeers.mapIndexed { index, peer ->
            if (index < 4) peer.copy(isActive = false) else peer
        }
        peersFlow.value = updatedPeers
        advanceTimeBy(1000)

        assertTrue(useCase.isCircuitOpen.value)
        verify { networkEventRepository.pushEvent(match { it.contains("WARNING") }) }
    }

    @Test
    fun `does not activate circuit breaker on cluster smaller than MIN_CLUSTER_SIZE`() = runTest {
        val useCase = CircuitBreakerUseCase(
            peerRepository = peerRepository,
            networkEventRepository = networkEventRepository,
            applicationScope = backgroundScope
        )
        useCase.currentTimeProvider = { testScheduler.currentTime }
        useCase.startupTimeMs = -CircuitBreakerUseCase.STARTUP_GRACE_MS - 1L

        val initialPeers = createPeers(2, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        val updatedPeers = initialPeers.map { it.copy(isActive = false) }
        peersFlow.value = updatedPeers
        advanceTimeBy(1000)

        assertFalse("Un micro-cluster < 3 pairs ne doit jamais activer le circuit", useCase.isCircuitOpen.value)
    }

    /**
     * La réévaluation s'exécute toutes les 2 min (120 000ms). Le churn (ajouté à t≈1 000ms)
     * sort de la fenêtre glissante de 5 min uniquement quand currentTime > 301 000ms.
     *
     * Calendrier des réévaluations :
     *   t=122 000ms → churn toujours dans fenêtre → reschedule
     *   t=242 000ms → churn toujours dans fenêtre → reschedule
     *   t=362 000ms → churn < windowStart(62 000ms) → PURGÉ → circuit se ferme
     *
     * On avance donc à t=363 000ms (361 000 + 1 000 + 1 000 de setup = 363 000ms).
     */
    @Test
    fun `deactivates circuit breaker after 2 minutes if churn is less than 10%`() = runTest {
        val useCase = CircuitBreakerUseCase(
            peerRepository = peerRepository,
            networkEventRepository = networkEventRepository,
            applicationScope = backgroundScope
        )
        useCase.currentTimeProvider = { testScheduler.currentTime }
        useCase.startupTimeMs = -CircuitBreakerUseCase.STARTUP_GRACE_MS - 1L

        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        val highChurnPeers = initialPeers.mapIndexed { index, peer ->
            if (index < 4) peer.copy(isActive = false) else peer
        }
        peersFlow.value = highChurnPeers
        advanceTimeBy(1000)
        assertTrue(useCase.isCircuitOpen.value)

        // Avancer jusqu'à la 3e réévaluation (t=362 000ms) où le churn est purgé
        advanceTimeBy(361_000)
        advanceTimeBy(1000)

        assertFalse("Le circuit doit se fermer après la 3e réévaluation (churn purgé)", useCase.isCircuitOpen.value)
    }
}
