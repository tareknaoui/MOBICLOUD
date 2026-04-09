/*
 * Copyright 2023 Atick Faisal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobicloud.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mobicloud.core.extensions.asOneTimeEvent
import com.mobicloud.core.extensions.stateInDelayed
import com.mobicloud.core.preferences.data.UserPreferencesDataSource
import com.mobicloud.core.preferences.model.UserDataPreferences
import com.mobicloud.core.ui.utils.UiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Annotates a ViewModel class that is managed by Hilt's dependency injection system.
 *
 * @constructor Creates a [MainActivityViewModel] instance.
 * @param userPreferencesDataSource The repository providing access to user data.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    userPreferencesDataSource: UserPreferencesDataSource,
) : ViewModel() {

    /**
     * Represents the state of the UI for user data.
     */
    val uiState: StateFlow<UiState<UserDataPreferences>> =
        userPreferencesDataSource.getUserDataPreferences()
            .map { userData -> UiState(userData) }
            .catch { e -> UiState(UserDataPreferences(), error = e.asOneTimeEvent()) }
            .stateInDelayed(UiState(UserDataPreferences(), loading = true), viewModelScope)
}
