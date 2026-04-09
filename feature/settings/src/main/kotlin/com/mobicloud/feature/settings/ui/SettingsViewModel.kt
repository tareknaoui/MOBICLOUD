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

package com.mobicloud.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mobicloud.core.extensions.asOneTimeEvent
import com.mobicloud.core.ui.utils.UiState
import com.mobicloud.core.ui.utils.getPreferredLocale
import com.mobicloud.core.ui.utils.setLanguagePreference
import com.mobicloud.core.ui.utils.updateWith
import com.mobicloud.data.model.settings.DarkThemeConfig
import com.mobicloud.data.model.settings.Language
import com.mobicloud.data.model.settings.Settings
import com.mobicloud.data.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * [ViewModel] for [SettingsDialog].
 *
 * @param settingsRepository [SettingsRepository].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _settingsUiState = MutableStateFlow(UiState(Settings()))
    val settingsUiState = _settingsUiState
        .onStart { updateSettings() }
        // Can't use stateInDelayed here because language change doesn't update
        // after activity restart, as it caches value for 5 seconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), UiState(Settings()))

    private fun updateSettings() {
        settingsRepository.getSettings()
            .map { UiState(it.copy(language = getPreferredLanguage())) }
            .onEach { state -> _settingsUiState.update { state } }
            .catch { e -> UiState(Settings(), error = e.asOneTimeEvent()) }
            .launchIn(viewModelScope)
    }

    fun updateDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        _settingsUiState.updateWith {
            settingsRepository.setDarkThemeConfig(
                darkThemeConfig,
            )
        }
    }

    fun updateDynamicColorPreference(useDynamicColor: Boolean) {
        _settingsUiState.updateWith {
            settingsRepository.setDynamicColorPreference(useDynamicColor)
        }
    }

    fun updateLanguagePreference(language: Language) {
        setLanguagePreference(language.code)
    }

    fun signOut() {
        _settingsUiState.updateWith { settingsRepository.signOut() }
    }

    private fun getPreferredLanguage(): Language {
        val preferredLanguage = getPreferredLocale().language
        return when (preferredLanguage) {
            "ar" -> Language.ARABIC
            else -> Language.ENGLISH
        }
    }
}
