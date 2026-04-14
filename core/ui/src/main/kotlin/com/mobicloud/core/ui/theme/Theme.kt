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

package com.mobicloud.core.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Light default theme color scheme
 */
@VisibleForTesting
val LightDefaultColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple10,
    secondary = Orange40,
    onSecondary = Color.White,
    secondaryContainer = Orange90,
    onSecondaryContainer = Orange10,
    tertiary = Blue40,
    onTertiary = Color.White,
    tertiaryContainer = Blue90,
    onTertiaryContainer = Blue10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = DarkPurpleGray99,
    onBackground = DarkPurpleGray10,
    surface = DarkPurpleGray99,
    onSurface = DarkPurpleGray10,
    surfaceVariant = PurpleGray90,
    onSurfaceVariant = PurpleGray30,
    inverseSurface = DarkPurpleGray20,
    inverseOnSurface = DarkPurpleGray95,
    outline = PurpleGray50,
)

/**
 * Dark default theme color scheme
 */
@VisibleForTesting
val DarkDefaultColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = PureBlack,
    primaryContainer = Color(0xFF003300),
    onPrimaryContainer = TerminalGreen,
    secondary = PrimaryText,
    onSecondary = PureBlack,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = PrimaryText,
    tertiary = Blue80,
    onTertiary = Blue20,
    tertiaryContainer = Blue30,
    onTertiaryContainer = Blue90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = PureBlack,
    onBackground = PrimaryText,
    surface = PureBlack,
    onSurface = PrimaryText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SecondaryText,
    inverseSurface = PrimaryText,
    inverseOnSurface = PureBlack,
    outline = SecondaryText,
)

/**
 * Now in Android theme.
 *
 * @param darkTheme Whether the theme should use a dark color scheme (follows system by default).
 * @param disableDynamicTheming If `true`, disables the use of dynamic theming, even when it is
 *        supported. This parameter has no effect if [androidTheme] is `true`.
 */
@Composable
fun JetpackTheme(
    content: @Composable () -> Unit,
) {
    // Strictly override to DarkDefaultColorScheme to enforce OLED rule
    val colorScheme = DarkDefaultColorScheme
    
    // Gradient colors
    val gradientColors = GradientColors() // Empty
    
    // Background theme with 0dp elevation for hardware/utilitarian aesthetic
    val defaultBackgroundTheme = BackgroundTheme(
        color = colorScheme.background,
        tonalElevation = 0.dp,
    )

    val tintTheme = TintTheme()
    // Composition locals
    CompositionLocalProvider(
        LocalGradientColors provides gradientColors,
        LocalBackgroundTheme provides defaultBackgroundTheme,
        LocalTintTheme provides tintTheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = JetpackTypography,
            content = content,
        )
    }
}

/**
 * Checks if the current device supports dynamic theming.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
