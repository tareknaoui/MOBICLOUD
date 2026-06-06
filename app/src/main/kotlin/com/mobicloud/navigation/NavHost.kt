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

package com.mobicloud.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mobicloud.presentation.dashboard.DashboardRoute
import com.mobicloud.presentation.dashboard.DashboardScreen
import com.mobicloud.presentation.explorer.ExplorerRoute
import com.mobicloud.presentation.explorer.ExplorerScreen
import com.mobicloud.presentation.network.NetworkRoute
import com.mobicloud.presentation.network.NetworkScreen
import com.mobicloud.presentation.onboarding.InitRoute
import com.mobicloud.presentation.onboarding.InitScreen
import com.mobicloud.presentation.onboarding.PermissionsRoute
import com.mobicloud.presentation.onboarding.PermissionsScreen
import com.mobicloud.presentation.onboarding.ProfileSetupRoute
import com.mobicloud.presentation.onboarding.ProfileSetupScreen
import com.mobicloud.presentation.onboarding.CloudAuthRoute
import com.mobicloud.presentation.onboarding.CloudAuthScreen
import com.mobicloud.presentation.onboarding.RestoreIdentityRoute
import com.mobicloud.presentation.onboarding.RestoreIdentityScreen
import com.mobicloud.presentation.onboarding.WelcomeRoute
import com.mobicloud.presentation.onboarding.WelcomeScreen
import com.mobicloud.presentation.pin.PinLockRoute
import com.mobicloud.presentation.pin.PinLockScreen
import com.mobicloud.presentation.pin.PinSetupRoute
import com.mobicloud.presentation.pin.PinSetupScreen
import com.mobicloud.presentation.settings.SettingsRoute
import com.mobicloud.presentation.settings.SettingsScreen
import com.mobicloud.presentation.trash.TrashRoute
import com.mobicloud.presentation.trash.TrashScreen
import com.mobicloud.ui.JetpackAppState
import com.mobicloud.core.ui.utils.SnackbarAction

@Composable
fun JetpackNavHost(
    appState: JetpackAppState,
    onShowSnackbar: suspend (String, SnackbarAction, Throwable?) -> Boolean,
    hasCompletedOnboarding: Boolean,
    hasPinSet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    val startDestination = when {
        !hasCompletedOnboarding -> WelcomeRoute
        hasPinSet -> PinLockRoute
        else -> DashboardRoute
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onFinish = {
                    navController.navigate(PermissionsRoute) {
                        popUpTo(WelcomeRoute) { inclusive = true }
                    }
                },
                onRestoreAccount = {
                    navController.navigate(RestoreIdentityRoute)
                },
                onCloudAuth = {
                    navController.navigate(CloudAuthRoute)
                },
            )
        }
        composable<RestoreIdentityRoute> {
            RestoreIdentityScreen(
                onRestored = {
                    // popUpTo(0) clears the whole back stack up to root, works whether we came
                    // from WelcomeScreen (onboarding) or SettingsScreen (in-app restore).
                    navController.navigate(DashboardRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<CloudAuthRoute> {
            CloudAuthScreen(
                onSuccess = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(WelcomeRoute) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<PermissionsRoute> {
            PermissionsScreen(
                onNavigateToInit = {
                    navController.navigate(ProfileSetupRoute)
                },
            )
        }
        composable<ProfileSetupRoute> {
            ProfileSetupScreen(
                onContinue = {
                    navController.navigate(InitRoute)
                }
            )
        }
        composable<InitRoute> {
            InitScreen(
                onNavigateToDashboard = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(PermissionsRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<PinLockRoute> {
            PinLockScreen(
                onUnlocked = {
                    navController.navigate(DashboardRoute) {
                        popUpTo(PinLockRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<PinSetupRoute> {
            PinSetupScreen(
                onDone = { navController.popBackStack() },
                onSkip = { navController.popBackStack() },
            )
        }
        composable<DashboardRoute> {
            DashboardScreen()
        }
        composable<ExplorerRoute> {
            ExplorerScreen(
                onNavigateToTrash = { navController.navigate(TrashRoute) }
            )
        }
        composable<TrashRoute> {
            TrashScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onRestoreIdentity = { navController.navigate(RestoreIdentityRoute) },
                onSetupPin = { navController.navigate(PinSetupRoute) },
            )
        }
        composable<NetworkRoute> {
            NetworkScreen()
        }
    }
}
