package com.mobicloud.presentation.onboarding

import com.mobicloud.domain.repository.NetworkServiceController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InitViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var networkServiceController: NetworkServiceController
    private lateinit var viewModel: InitViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        networkServiceController = mockk(relaxed = true) {
            every { startService() } returns Result.success(Unit)
        }
        viewModel = InitViewModel(networkServiceController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startInit émet CreatingIdentity en premier`() = runTest {
        viewModel.startInit()
        testDispatcher.scheduler.runCurrent()
        assertEquals(InitStep.CreatingIdentity, viewModel.currentStep.value)
    }

    @Test
    fun `startInit émet SearchingMembers après le premier délai`() = runTest {
        viewModel.startInit()
        advanceTimeBy(2_001L)
        assertEquals(InitStep.SearchingMembers, viewModel.currentStep.value)
    }

    @Test
    fun `startInit émet ConnectingToGroup après deux délais`() = runTest {
        viewModel.startInit()
        advanceTimeBy(4_001L)
        assertEquals(InitStep.ConnectingToGroup, viewModel.currentStep.value)
    }

    @Test
    fun `startInit émet Done et appelle startService`() = runTest {
        viewModel.startInit()
        advanceUntilIdle()
        assertEquals(InitStep.Done, viewModel.currentStep.value)
        verify(exactly = 1) { networkServiceController.startService() }
    }

    @Test
    fun `startInit émet Done après timeout de 8 secondes`() = runTest {
        // Même si les délais internes dépassent 8s (ex: simulés), Done est émis dans les 8s
        viewModel.startInit()
        advanceTimeBy(8_001L)
        assertEquals(InitStep.Done, viewModel.currentStep.value)
    }
}
