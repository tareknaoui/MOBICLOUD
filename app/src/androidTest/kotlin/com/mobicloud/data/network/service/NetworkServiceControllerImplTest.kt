package com.mobicloud.data.network.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobicloud.domain.repository.NetworkServiceController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [NetworkServiceControllerImpl].
 *
 * NOTE: Full coverage of MulticastLock lifecycle (acquire/release) requires Robolectric
 * with a mocked WifiManager — tracked in deferred-work.md.
 */
@RunWith(AndroidJUnit4::class)
class NetworkServiceControllerImplTest {

    private lateinit var controller: NetworkServiceController

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        controller = NetworkServiceControllerImpl(context)
    }

    @Test
    fun startService_returnsResultWithoutThrowing() {
        // Verifies that startService() returns a Result (success or failure) without throwing,
        // and that no unhandled exception escapes the implementation boundary.
        val result = controller.startService()
        // On a test device, the service may or may not start depending on permissions,
        // but the controller MUST return a Result rather than throw.
        assertFalse(
            "startService() must not throw — failures must be wrapped in Result.failure()",
            result == null
        )
    }

    @Test
    fun stopService_returnsSuccessWithoutThrowing() {
        // Verifies that stopService() always returns Result.success even when the service
        // was never started (stopping an already-stopped service is a no-op on Android).
        val result = controller.stopService()
        assertTrue(
            "stopService() on an already-stopped service must return Result.success()",
            result.isSuccess
        )
    }

    @Test
    fun startThenStop_noExceptionEscapes() {
        // Verifies the full start → stop lifecycle does not throw at the controller boundary.
        val startResult = controller.startService()
        val stopResult = controller.stopService()
        assertFalse("startService result must not be null", startResult == null)
        assertFalse("stopService result must not be null", stopResult == null)
    }
}
