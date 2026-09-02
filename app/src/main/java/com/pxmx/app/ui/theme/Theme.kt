package com.pxmx.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.ui.components.DarkTechPalette
import com.pxmx.app.ui.components.LightTechPalette
import com.pxmx.app.ui.components.LocalTechColors

// Proxmox orange
private val PveOrange = Color(0xFFE57000)
private val PveOrangeLight = Color(0xFFFF9800)

/** True AMOLED / OLED — pure black, minimal elevation glow. */
private val OledDarkColors = darkColorScheme(
    primary = PveOrangeLight,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3D2200),
    onPrimaryContainer = PveOrangeLight,
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFE0E0E0),
    // Keep tertiary neutral — no light-blue accents on OLED.
    tertiary = Color(0xFFE0E0E0),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2A2A2A),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color.Black,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = PveOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8D6),
    onPrimaryContainer = Color(0xFF3D2200),
    secondary = Color(0xFF5A5A5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2E2),
    onSecondaryContainer = Color(0xFF202020),
    tertiary = Color(0xFF5E5E5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDADADA),
    onTertiaryContainer = Color(0xFF202020),
    background = Color(0xFFE9E9E9),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFF2F2F2),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE4E4E4),
    onSurfaceVariant = Color(0xFF5A5A5A),
    surfaceContainerLowest = Color(0xFFF8F8F8),
    surfaceContainerLow = Color(0xFFF4F4F4),
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    surfaceContainerHighest = Color(0xFFDFDFDF),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFDCDCDC),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    inverseSurface = Color(0xFF202020),
    inverseOnSurface = Color(0xFFE9E9E9),
    scrim = Color.Black,
)

@Composable
fun ProxmoxTheme(
    themeMode: ThemeMode = ThemeMode.OLED_DARK,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.OLED_DARK -> true
    }
    val colorScheme = if (useDark) OledDarkColors else LightColors
    val techPalette = if (useDark) DarkTechPalette else LightTechPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(LocalTechColors provides techPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
