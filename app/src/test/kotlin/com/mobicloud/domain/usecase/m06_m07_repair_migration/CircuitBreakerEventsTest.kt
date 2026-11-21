package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.usecase.experiments.ProtocolEventRecorder
import com.mobicloud.domain.usecase.experiments.ProtocolEventType
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le circuit-breaker journalise ses décisions pour l'évaluation par injection de pannes (R1-5). */
@OptIn(ExperimentalCoroutinesApi::class)
class CircuitBreakerEventsTest {

    private data class Event(val type: ProtocolEventType, val value: Double, val detail: String)

    private val events = mutableListOf<Event>()
    private val recorder = object : ProtocolEventRecorder {
        override fun record(type: ProtocolEventType, value: Double, detail: String) {
            events += Event(type, value, detail)
        }
    }

    private val peersFlow = MutableStateFlow<List<Peer>>(emptyList())
    private val peerRepository = mockk<PeerRepository> { every { peers } returns peersFlow }
    private val networkEvents = mockk<NetworkEventRepository> { every { pushEvent(any()) } just Runs }

    private fun peers(count: Int, inactive: Int = 0) = (1..count).map { i ->
        Peer(identity = NodeIdentity("node$i", ByteArray(0)), lastSeenTimestampMs = 0L, isActive = i > inactive)
    }

    @Test
    fun `mass churn records the inactive peers, the opening and the later closing`() = runTest {
        val breaker = CircuitBreakerUseCase(peerRepository, networkEvents, backgroundScope, recorder)
        breaker.currentTimeProvider = { testScheduler.currentTime }
        breaker.startupTimeMs = -CircuitBreakerUseCase.STARTUP_GRACE_MS - 1L

        peersFlow.value = peers(10)
        advanceTimeBy(1_000)
        peersFlow.value = peers(10, inactive = 4) // 40 % > 30 %
        advanceTimeBy(1_000)

        assertTrue(breaker.isCircuitOpen.value)
        val inactive = events.single { it.type == ProtocolEventType.PEER_INACTIVE }
        assertEquals(4.0, inactive.value, 0.0)
        assertEquals(0.4, events.single { it.type == ProtocolEventType.CIRCUIT_OPEN }.value, 1e-9)

        peersFlow.value = peers(10) // retour de tous les pairs
        advanceTimeBy(200_000)      // la fenêtre de churn (2 min) expire, réévaluation toutes les 30 s

        assertFalse(breaker.isCircuitOpen.value)
        assertEquals(1, events.count { it.type == ProtocolEventType.CIRCUIT_CLOSE })
        assertEquals(
            listOf(ProtocolEventType.PEER_INACTIVE, ProtocolEventType.CIRCUIT_OPEN, ProtocolEventType.CIRCUIT_CLOSE),
            events.map { it.type }
        )
    }

    @Test
    fun `an isolated departure is recorded without opening the circuit`() = runTest {
        val breaker = CircuitBreakerUseCase(peerRepository, networkEvents, backgroundScope, recorder)
        breaker.currentTimeProvider = { testScheduler.currentTime }
        breaker.startupTimeMs = -CircuitBreakerUseCase.STARTUP_GRACE_MS - 1L

        peersFlow.value = peers(7)
        advanceTimeBy(1_000)
        peersFlow.value = peers(7, inactive = 1) // 14 %
        advanceTimeBy(1_000)

        assertFalse(breaker.isCircuitOpen.value)
        assertEquals(listOf(ProtocolEventType.PEER_INACTIVE), events.map { it.type })
    }
}
