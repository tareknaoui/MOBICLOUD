package com.mobicloud.domain.models.m11_join

import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterConstantsTest {

    @Test
    fun `JOIN_REQUEST_TIMEOUT_MS est inferieur a ISOLATION_BACKOFF_MS`() {
        assertTrue(
            "JOIN_REQUEST_TIMEOUT_MS ($JOIN_REQUEST_TIMEOUT_MS) doit être < ISOLATION_BACKOFF_MS ($ISOLATION_BACKOFF_MS)",
            JOIN_REQUEST_TIMEOUT_MS < ISOLATION_BACKOFF_MS
        )
    }

    @Test
    fun `ISOLATION_BACKOFF_MS est inferieur a SP_TIMEOUT_MS`() {
        assertTrue(
            "ISOLATION_BACKOFF_MS ($ISOLATION_BACKOFF_MS) doit être < SP_TIMEOUT_MS ($SP_TIMEOUT_MS)",
            ISOLATION_BACKOFF_MS < SP_TIMEOUT_MS
        )
    }

    @Test
    fun `invariant global JOIN_REQUEST_TIMEOUT_MS lt ISOLATION_BACKOFF_MS lt SP_TIMEOUT_MS`() {
        assertTrue(JOIN_REQUEST_TIMEOUT_MS < ISOLATION_BACKOFF_MS && ISOLATION_BACKOFF_MS < SP_TIMEOUT_MS)
    }

    @Test
    fun `MAX_CLUSTER_SIZE est positif`() {
        assertTrue(MAX_CLUSTER_SIZE > 0)
    }

    @Test
    fun `HEARTBEAT_INTERVAL_MS est positif`() {
        assertTrue(HEARTBEAT_INTERVAL_MS > 0)
    }
}
