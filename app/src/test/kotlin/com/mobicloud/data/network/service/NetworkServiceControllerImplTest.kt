package com.mobicloud.data.network.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.mobicloud.domain.models.ServiceStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkServiceControllerImplTest {

    private lateinit var mockContext: Context
    private lateinit var controller: NetworkServiceControllerImpl

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        controller = NetworkServiceControllerImpl(mockContext)
    }

    // --- État initial ---

    @Test
    fun `état initial est STOPPED`() {
        assertEquals(ServiceStatus.STOPPED, controller.serviceStatus.value)
    }

    // --- startService() succès → RUNNING ---

    @Test
    fun `startService réussit → transition STOPPED vers RUNNING`() {
        // Arrange: startForegroundService ne lève pas d'exception (relaxed mock)
        every { mockContext.startForegroundService(any<Intent>()) } returns mockk()
        every { mockContext.startService(any<Intent>()) } returns mockk()

        // Act
        val result = controller.startService()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(ServiceStatus.RUNNING, controller.serviceStatus.value)
    }

    // --- startService() SecurityException → ERROR ---

    @Test
    fun `startService lève SecurityException → transition vers ERROR`() {
        // Arrange: simuler ForegroundServiceStartNotAllowedException (SecurityException)
        every { mockContext.startForegroundService(any<Intent>()) } throws SecurityException("Not allowed in background")
        every { mockContext.startService(any<Intent>()) } throws SecurityException("Not allowed in background")

        // Act
        val result = controller.startService()

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals(ServiceStatus.ERROR, controller.serviceStatus.value)
    }

    // --- startService() exception générique → ERROR ---

    @Test
    fun `startService lève RuntimeException → transition vers ERROR`() {
        every { mockContext.startForegroundService(any<Intent>()) } throws RuntimeException("Unexpected failure")
        every { mockContext.startService(any<Intent>()) } throws RuntimeException("Unexpected failure")

        val result = controller.startService()

        assertTrue(result.isFailure)
        assertEquals(ServiceStatus.ERROR, controller.serviceStatus.value)
    }

    // --- stopService() → STOPPED ---

    @Test
    fun `stopService réussit → transition vers STOPPED`() {
        // Arrange: démarrer d'abord le service
        every { mockContext.startForegroundService(any<Intent>()) } returns mockk()
        every { mockContext.startService(any<Intent>()) } returns mockk()
        controller.startService()
        assertEquals(ServiceStatus.RUNNING, controller.serviceStatus.value)

        // Act
        every { mockContext.stopService(any<Intent>()) } returns true
        val result = controller.stopService()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(ServiceStatus.STOPPED, controller.serviceStatus.value)
    }
}
