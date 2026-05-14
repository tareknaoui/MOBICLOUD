package com.mobicloud.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.core.preferences.data.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
) : ViewModel() {

    fun markOnboardingCompleted() {
        viewModelScope.launch {
            userPreferencesDataSource.setOnboardingCompleted()
        }
    }
}
