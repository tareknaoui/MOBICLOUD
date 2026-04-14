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

package com.mobicloud

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import com.mobicloud.compose.MainActivityViewModel
import com.mobicloud.domain.repository.NetworkServiceController
import com.mobicloud.compose.ui.JetpackApp
import com.mobicloud.ui.rememberJetpackAppState
import com.mobicloud.core.network.utils.NetworkUtils
import com.mobicloud.core.preferences.model.DarkThemeConfigPreferences
import com.mobicloud.core.preferences.model.UserDataPreferences
import com.mobicloud.core.ui.extensions.checkForPermissions
import com.mobicloud.core.ui.extensions.isSystemInDarkTheme
import com.mobicloud.core.ui.theme.JetpackTheme
import com.mobicloud.core.ui.utils.UiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * For now, extend from AppCompatActivity.
 * Otherwise, setApplicationLocales will do nothing.
 *
 * Extending from AppCompatActivity requires to use an AppCompat theme for the Activity.
 * In the manifest, for the activity, use android:theme="@style/Theme.AppCompat"
 * Otherwise, the application will crash.
 *
 * The alternative is to replace AppCompatDelegate with the Framework APIs.
 * The Frameworks APIs are not backwards compatible, like AppCompatDelegate, and so work for T+.
 * However, with the Framework APIs, you can use Compose themes and extend from ComponentActivity.
 * Framework APIs: https://developer.android.com/about/versions/13/features/app-languages#framework-impl
 * Read more: https://developer.android.com/guide/topics/resources/app-languages#gradle-config
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val permissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Inject
    lateinit var networkUtils: NetworkUtils

    @Inject
    lateinit var networkServiceController: NetworkServiceController

    private val viewModel: MainActivityViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // We keep this as a mutable state, so that we can track changes inside the composition.
        // This allows us to react to dark/light mode changes.
        var themeSettings by mutableStateOf(
            ThemeSettings(darkTheme = resources.configuration.isSystemInDarkTheme),
        )

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    isSystemInDarkTheme(),
                    viewModel.uiState,
                ) { isSystemDark, uiState ->
                    ThemeSettings(
                        darkTheme = shouldUseDarkTheme(isSystemDark, uiState),
                        disableDynamicTheming = shouldDisableDynamicTheming(uiState),
                    )
                }
                    .onEach { themeSettings = it }
                    .map { it.darkTheme }
                    .distinctUntilChanged()
                    .collect { darkTheme ->
                        // Turn off the decor fitting system windows, which allows us to handle insets,
                        // including IME animations, and go edge-to-edge.
                        // This is the same parameters as the default enableEdgeToEdge call, but we manually
                        // resolve whether or not to show dark theme using uiState, since it can be different
                        // than the configuration's dark theme value based on the user preference.
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(
                                lightScrim = android.graphics.Color.TRANSPARENT,
                                darkScrim = android.graphics.Color.TRANSPARENT,
                            ) { darkTheme },
                            navigationBarStyle = SystemBarStyle.auto(
                                lightScrim = lightScrim,
                                darkScrim = darkScrim,
                            ) { darkTheme },
                        )
                    }
            }
        }

        // Keep the splash screen on-screen until the UI state is loaded. This condition is
        // evaluated each time the app needs to be redrawn so it should be fast to avoid blocking
        // the UI.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.loading }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val appState = rememberJetpackAppState(
                isUserLoggedIn = isUserLoggedIn(uiState),
                userProfilePictureUri = getUserProfilePictureUri(uiState),
                windowSizeClass = calculateWindowSizeClass(this),
                networkUtils = networkUtils,
            )
            JetpackTheme {
                JetpackApp(appState)
            }
        }

        checkForPermissions(permissions) {
            // Ce callback s'exécute depuis le foreground de l'Activity — safe Android 12+
            val result = networkServiceController.startService()
            result.onFailure { e ->
                Log.e("MainActivity", "[P2P-SERVICE] Échec démarrage service: ${e.message}", e)
            }
        }
    }
}

/**
 * Returns `true` if the dynamic color is disabled, as a function of the [uiState].
 */
private fun shouldDisableDynamicTheming(
    uiState: UiState<UserDataPreferences>,
): Boolean {
    return if (uiState.loading || uiState.error.peekContent() != null) {
        false
    } else {
        !uiState.data.useDynamicColor
    }
}

/**
 * Returns `true` if dark theme should be used, as a function of the [uiState] and the
 * current system context.
 */
private fun shouldUseDarkTheme(
    isSystemInDarkTheme: Boolean,
    uiState: UiState<UserDataPreferences>,
): Boolean {
    return if (uiState.loading || uiState.error.peekContent() != null) {
        isSystemInDarkTheme
    } else {
        when (uiState.data.darkThemeConfigPreferences) {
            DarkThemeConfigPreferences.FOLLOW_SYSTEM -> isSystemInDarkTheme
            DarkThemeConfigPreferences.LIGHT -> false
            DarkThemeConfigPreferences.DARK -> true
        }
    }
}

/**
 * Determines whether a user is logged in based on the provided [UiState].
 *
 * @param uiState The UI state representing the user data.
 * @return `true` if the user is considered logged in; `false` otherwise.
 */
private fun isUserLoggedIn(uiState: UiState<UserDataPreferences>): Boolean {
    // User is considered logged in during loading (assuming ongoing session).
    return uiState.data.id.isNotEmpty() || uiState.loading
}

/**
 * Returns the user profile picture URI string from the provided [UiState].
 *
 * @param uiState The UI state representing the user data.
 * @return The user profile picture URI string, or `null` if the user is not logged in or an error occurred.
 */
private fun getUserProfilePictureUri(uiState: UiState<UserDataPreferences>): String? {
    return if (uiState.loading || uiState.error.peekContent() != null) {
        null
    } else {
        uiState.data.profilePictureUriString
    }
}

/**
 * The default light scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=35-38;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val lightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)

/**
 * The default dark scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=40-44;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val darkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Class for the system theme settings.
 * This wrapping class allows us to combine all the changes and prevent unnecessary recompositions.
 */
data class ThemeSettings(
    val darkTheme: Boolean,
    val disableDynamicTheming: Boolean = true,
)
