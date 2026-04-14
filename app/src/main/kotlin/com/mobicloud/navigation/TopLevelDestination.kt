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

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.mobicloud.compose.R
import com.mobicloud.presentation.dashboard.DashboardRoute
import com.mobicloud.presentation.explorer.ExplorerRoute
import com.mobicloud.presentation.settings.SettingsRoute
import kotlin.reflect.KClass

/**
 * Enum class representing top-level destinations in a navigation system.
 *
 * @property selectedIcon The selected icon associated with the destination.
 * @property unselectedIcon The unselected icon associated with the destination.
 * @property iconTextId The resource ID for the icon's content description text.
 * @property titleTextId The resource ID for the title text.
 * @property route The route associated with the destination.
 */
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val iconTextId: Int,
    @StringRes val titleTextId: Int,
    val route: KClass<*>,
) {
    DASHBOARD(
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        iconTextId = R.string.dashboard,
        titleTextId = R.string.dashboard,
        route = DashboardRoute::class,
    ),
    EXPLORER(
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        iconTextId = R.string.explorer,
        titleTextId = R.string.explorer,
        route = ExplorerRoute::class,
    ),
    SETTINGS(
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        iconTextId = R.string.settings,
        titleTextId = R.string.settings,
        route = SettingsRoute::class,
    ),
}
