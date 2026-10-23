package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

internal val DarkColorScheme = darkColorScheme(
    primary = GhostSky,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF17354A),
    onPrimaryContainer = Color(0xFFD3ECFF),
    secondary = GhostTeal,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF173244),
    onSecondaryContainer = GhostTextPrimaryDark,
    background = GhostBgDark,
    onBackground = GhostTextPrimaryDark,
    surface = GhostSurfaceDark,
    onSurface = GhostTextPrimaryDark,
    surfaceVariant = GhostCardDark,
    surfaceContainerLowest = GhostBgDark,
    surfaceContainerLow = GhostSurfaceDark,
    surfaceContainer = Color(0xFF182333),
    surfaceContainerHigh = GhostCardDark,
    surfaceContainerHighest = Color(0xFF28374A),
    outlineVariant = GhostBorderDark,
    onSurfaceVariant = GhostTextSecondaryDark,
    outline = GhostBorderDark,
    error = GhostRed
)

internal val LightColorScheme = lightColorScheme(
    primary = GhostSkyDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F0FA),
    onPrimaryContainer = Color(0xFF064A70),
    secondary = Color(0xFF047857),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4F1FB),
    onSecondaryContainer = GhostTextPrimaryLight,
    background = GhostBgLight,
    onBackground = GhostTextPrimaryLight,
    surface = GhostSurfaceLight,
    onSurface = GhostTextPrimaryLight,
    surfaceVariant = GhostCardLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F8FB),
    surfaceContainer = Color(0xFFF0F4F8),
    surfaceContainerHigh = Color(0xFFE7EDF3),
    surfaceContainerHighest = Color(0xFFDEE6EF),
    outlineVariant = Color(0xFFDBE3EC),
    onSurfaceVariant = GhostTextSecondaryLight,
    outline = GhostBorderLight,
    error = GhostRed
)

@Composable
fun GhostGramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = androidx.compose.ui.platform.LocalView.current

    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colorScheme.surface.toArgb()))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            // Android 7 cannot display dark navigation buttons against a light background.
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                window.navigationBarColor = android.graphics.Color.BLACK
            }
            
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
