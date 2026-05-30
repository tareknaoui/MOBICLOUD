package com.mobicloud.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.core.preferences.data.UserPreferencesDataSource
import com.mobicloud.core.preferences.model.PreferencesUserProfile
import com.mobicloud.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    fun saveProfile(displayName: String, avatarUri: String? = null) {
        viewModelScope.launch {
            val nodeId = identityRepository.getIdentity().getOrNull()?.nodeId ?: ""
            userPreferencesDataSource.setUserProfile(
                PreferencesUserProfile(
                    id = nodeId,
                    userName = displayName.trim(),
                    profilePictureUriString = avatarUri
                )
            )
        }
    }
}
