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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private lateinit var useCase: CircuitBreakerUseCase
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        peerRepository = mockk()
        networkEventRepository = mockk()
        peersFlow = MutableStateFlow(emptyList())
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)

        every { peerRepository.peers } returns peersFlow
        every { networkEventRepository.pushEvent(any()) } just Runs

        useCase = CircuitBreakerUseCase(
            peerRepository = peerRepository,
            networkEventRepository = networkEventRepository,
            applicationScope = testScope
        )
        useCase.currentTimeProvider = { testScope.testScheduler.currentTime }
    }

    private fun createPeers(count: Int, active: Boolean): List<Peer> {
        return (1..count).map { i ->
            Peer(
                identity = NodeIdentity("node$i", "pubkey"),
                lastSeenTimestampMs = 1000L,
                isActive = active
            )
        }
    }

    @Test
    fun `does not activate circuit breaker when churn is under 30%`() = testScope.runTest {
        // Initial state: 10 active peers
        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        // Only 2 peers go offline (20% churn) — sous le seuil
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
    fun `does not activate circuit breaker at exactly 30% churn boundary`() = testScope.runTest {
        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        // Exactement 3/10 = 30% : condition est churnRate > 0.3, donc 0.3 n'active PAS le breaker
        val updatedPeers = initialPeers.mapIndexed { index, peer ->
            if (index < 3) peer.copy(isActive = false) else peer
        }
        peersFlow.value = updatedPeers
        advanceTimeBy(1000)

        assertFalse("30% exact ne doit pas activer le circuit (condition est > 0.3)", useCase.isCircuitOpen.value)
    }

    @Test
    fun `activates circuit breaker when churn exceeds 30%`() = testScope.runTest {
        // Initial state: 10 active peers
        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        // 4 peers go offline = 40% churn > 30%
        val updatedPeers = initialPeers.mapIndexed { index, peer ->
            if (index < 4) peer.copy(isActive = false) else peer
        }
        peersFlow.value = updatedPeers
        advanceTimeBy(1000)

        assertTrue(useCase.isCircuitOpen.value)
        verify { networkEventRepository.pushEvent(match { it.contains("WARNING") }) }
    }

    @Test
    fun `does not activate circuit breaker on cluster smaller than MIN_CLUSTER_SIZE`() = testScope.runTest {
        // Cluster de 2 pairs (< 3) : même 100% de churn ne doit pas activer le breaker
        val initialPeers = createPeers(2, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        val updatedPeers = initialPeers.map { it.copy(isActive = false) }
        peersFlow.value = updatedPeers
        advanceTimeBy(1000)

        assertFalse("Un micro-cluster < 3 pairs ne doit jamais activer le circuit", useCase.isCircuitOpen.value)
    }

    /**
     * P6 — Fix du test fragile : avancer le temps au-delà de la fenêtre 5min (300s)
     * afin que les drops enregistrés soient purgés par la fenêtre glissante avant la réévaluation.
     */
    @Test
    fun `deactivates circuit breaker after 2 minutes if churn is less than 10%`() = testScope.runTest {
        // 1. État initial : 10 pairs actifs
        val initialPeers = createPeers(10, true)
        peersFlow.value = initialPeers
        advanceTimeBy(1000)

        // 2. Churn élevé : 40% → circuit s'ouvre
        val highChurnPeers = initialPeers.mapIndexed { index, peer ->
            if (index < 4) peer.copy(isActive = false) else peer
        }
        peersFlow.value = highChurnPeers
        advanceTimeBy(1000)
        assertTrue(useCase.isCircuitOpen.value)

        // P6 — Avancer au-delà des 5 minutes de fenêtre glissante (300 001 ms)
        // pour que la réévaluation à t+2min voie un churnHistory vide (tous les drops purgés)
        advanceTimeBy(301_000)

        // Laisser la réévaluation s'exécuter
        advanceTimeBy(1000)

        assertFalse("Le circuit doit se fermer après 2 min si churn < 10%", useCase.isCircuitOpen.value)
    }
}
