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

package com.mobicloud.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import com.mobicloud.ui.JetpackAppState
import com.mobicloud.core.ui.utils.SnackbarAction
import com.mobicloud.feature.auth.navigation.AuthNavGraph
import com.mobicloud.feature.auth.navigation.authNavGraph
import com.mobicloud.feature.auth.navigation.navigateToSignInScreen
import com.mobicloud.feature.auth.navigation.navigateToSignUpScreen
import com.mobicloud.feature.auth.navigation.signInScreen
import com.mobicloud.feature.auth.navigation.signUpScreen
import com.mobicloud.feature.home.navigation.HomeNavGraph
import com.mobicloud.feature.home.navigation.homeNavGraph
import com.mobicloud.feature.home.navigation.homeScreen
import com.mobicloud.feature.home.navigation.itemScreen
import com.mobicloud.feature.home.navigation.navigateToItemScreen
import com.mobicloud.feature.profile.navigation.profileScreen

/**
 * Composable function that sets up the navigation host for the Jetpack Compose application.
 *
 * @param appState The state of the Jetpack application, containing the navigation controller and user login status.
 * @param onShowSnackbar A lambda function to show a snackbar with a message and an action.
 * @param modifier The modifier to be applied to the NavHost.
 */
@Composable
fun JetpackNavHost(
    appState: JetpackAppState,
    onShowSnackbar: suspend (String, SnackbarAction, Throwable?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    val startDestination =
        if (appState.isUserLoggedIn) HomeNavGraph::class else AuthNavGraph::class
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        authNavGraph(
            nestedNavGraphs = {
                signInScreen(
                    onSignUpClick = navController::navigateToSignUpScreen,
                    onShowSnackbar = onShowSnackbar,
                )
                signUpScreen(
                    onSignInClick = navController::navigateToSignInScreen,
                    onShowSnackbar = onShowSnackbar,
                )
            },
        )
        homeNavGraph(
            nestedNavGraphs = {
                homeScreen(
                    onJetpackClick = navController::navigateToItemScreen,
                    onShowSnackbar = onShowSnackbar,
                )
                itemScreen(
                    onBackClick = navController::popBackStack,
                    onShowSnackbar = onShowSnackbar,
                )
            },
        )
        profileScreen(
            onShowSnackbar = onShowSnackbar,
        )
    }
}
