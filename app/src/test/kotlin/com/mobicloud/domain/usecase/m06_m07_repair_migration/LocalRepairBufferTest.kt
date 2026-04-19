package com.mobicloud.domain.usecase.m06_m07_repair_migration

import com.mobicloud.domain.models.RepairRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalRepairBufferTest {

    private lateinit var circuitBreakerUseCase: CircuitBreakerUseCase
    private lateinit var isCircuitOpenFlow: MutableStateFlow<Boolean>

    @Before
    fun setup() {
        circuitBreakerUseCase = mockk()
        isCircuitOpenFlow = MutableStateFlow(false)
        every { circuitBreakerUseCase.isCircuitOpen } returns isCircuitOpenFlow
    }

    @Test
    fun `enqueue drops oldest request when capacity exceeds 50`() = runTest {
        val localRepairBuffer = LocalRepairBuffer(circuitBreakerUseCase, backgroundScope)

        for (i in 1..50) {
            val dropped = localRepairBuffer.enqueue(RepairRequest("block$i", "192.168.0.1", 8080))
            assertNull("Ne devrait pas dropper avant capacité max ($i/50)", dropped)
        }

        val dropped = localRepairBuffer.enqueue(RepairRequest("block51", "192.168.0.1", 8080))

        assertNotNull("L'élément le plus ancien devrait être retourné comme dropped", dropped)
        assertEquals("block1", dropped!!.blockId)

        val drained = localRepairBuffer.drain()
        assertEquals(50, drained.size)
        assertEquals("block2", drained.first().blockId)
        assertEquals("block51", drained.last().blockId)
    }

    @Test
    fun `drain clears the buffer`() = runTest {
        val localRepairBuffer = LocalRepairBuffer(circuitBreakerUseCase, backgroundScope)

        localRepairBuffer.enqueue(RepairRequest("block1", "192.168.0.1", 8080))

        val drained1 = localRepairBuffer.drain()
        assertEquals(1, drained1.size)

        val drained2 = localRepairBuffer.drain()
        assertEquals(0, drained2.size)
    }

    @Test
    fun `enqueue returns null when buffer is not full`() = runTest {
        val localRepairBuffer = LocalRepairBuffer(circuitBreakerUseCase, backgroundScope)

        val dropped = localRepairBuffer.enqueue(RepairRequest("block1", "192.168.0.1", 8080))
        assertNull("Aucun drop attendu pour un buffer vide", dropped)
        assertEquals(1, localRepairBuffer.size())
    }

    @Test
    fun `drain returns empty list and keeps elements when circuit is open`() = runTest {
        val localRepairBuffer = LocalRepairBuffer(circuitBreakerUseCase, backgroundScope)

        localRepairBuffer.enqueue(RepairRequest("block1", "192.168.0.1", 8080))
        localRepairBuffer.enqueue(RepairRequest("block2", "192.168.0.1", 8080))

        isCircuitOpenFlow.value = true

        val drained1 = localRepairBuffer.drain()
        assertEquals(0, drained1.size)
        assertEquals(2, localRepairBuffer.size())

        isCircuitOpenFlow.value = false

        val drained2 = localRepairBuffer.drain()
        assertEquals(2, drained2.size)
        assertEquals(0, localRepairBuffer.size())
    }

    /**
     * P7 — Vérifier que les requêtes en attente sont émises automatiquement via [pendingAfterCircuitClose]
     * quand le circuit passe OPEN → CLOSED, sans attendre un COORDINATOR explicite.
     */
    @Test
    fun `pendingAfterCircuitClose emits buffered requests when circuit closes`() = runTest {
        val localRepairBuffer = LocalRepairBuffer(circuitBreakerUseCase, backgroundScope)

        isCircuitOpenFlow.value = true
        advanceTimeBy(100)

        localRepairBuffer.enqueue(RepairRequest("block1", "192.168.0.1", 8080))
        localRepairBuffer.enqueue(RepairRequest("block2", "192.168.0.1", 8080))
        assertEquals(2, localRepairBuffer.size())

        val emitted = mutableListOf<List<RepairRequest>>()
        val collectJob = backgroundScope.launch {
            localRepairBuffer.pendingAfterCircuitClose.collect { emitted.add(it) }
        }

        isCircuitOpenFlow.value = false
        advanceTimeBy(100)

        collectJob.cancel()

        assertEquals("Un seul lot devrait être émis", 1, emitted.size)
        assertEquals("Les 2 requêtes doivent être émises", 2, emitted.first().size)
        assertEquals("Le buffer doit être vide après émission", 0, localRepairBuffer.size())
    }
}
