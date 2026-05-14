package com.mobicloud.presentation.onboarding

import com.mobicloud.core.preferences.data.UserPreferencesDataSource
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPreferencesDataSource: UserPreferencesDataSource
    private lateinit var viewModel: PermissionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userPreferencesDataSource = mockk(relaxed = true)
        viewModel = PermissionsViewModel(userPreferencesDataSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `markOnboardingCompleted appelle setOnboardingCompleted`() = runTest {
        viewModel.markOnboardingCompleted()
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesDataSource.setOnboardingCompleted() }
    }
}
