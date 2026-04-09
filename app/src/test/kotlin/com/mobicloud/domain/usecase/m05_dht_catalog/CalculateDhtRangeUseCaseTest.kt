package com.mobicloud.domain.usecase.m05_dht_catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateDhtRangeUseCaseTest {
    
    private val useCase = CalculateDhtRangeUseCase()

    @Test
    fun testIsInRange_normalRange() {
        val nodeId = "5000"
        val successorId = "a000"
        
        // key >= 5000 and key < a000
        assertTrue(useCase(key = "6000", nodeId = nodeId, successorId = successorId))
        assertTrue(useCase(key = "5000", nodeId = nodeId, successorId = successorId))
        assertFalse(useCase(key = "a000", nodeId = nodeId, successorId = successorId))
        assertFalse(useCase(key = "4000", nodeId = nodeId, successorId = successorId))
    }

    @Test
    fun testIsInRange_wrapAroundRange() {
        // ID_DHT is near the end, and successor wraps around to the beginning
        val nodeId = "f000"
        val successorId = "2000"
        
        // key >= f000 OR key < 2000
        assertTrue(useCase(key = "f500", nodeId = nodeId, successorId = successorId))
        assertTrue(useCase(key = "1000", nodeId = nodeId, successorId = successorId))
        assertTrue(useCase(key = "f000", nodeId = nodeId, successorId = successorId))
        
        // out of range
        assertFalse(useCase(key = "3000", nodeId = nodeId, successorId = successorId))
        assertFalse(useCase(key = "e000", nodeId = nodeId, successorId = successorId))
    }

    @Test
    fun testIsInRange_singleNode() {
        val nodeId = "5000"
        val successorId = "5000"
        
        // If we are the only node, we cover the whole ring
        assertTrue(useCase(key = "1000", nodeId = nodeId, successorId = successorId))
        assertTrue(useCase(key = "5000", nodeId = nodeId, successorId = successorId))
        assertTrue(useCase(key = "9000", nodeId = nodeId, successorId = successorId))
    }
}
