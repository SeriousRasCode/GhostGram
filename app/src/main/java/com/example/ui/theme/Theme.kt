package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val DarkColorScheme = darkColorScheme(
    primary = GhostSky,
    onPrimary = Color.Black,
    primaryContainer = GhostCardDark,
    onPrimaryContainer = GhostSky,
    secondary = GhostTeal,
    onSecondary = Color.Black,
    secondaryContainer = GhostTealDim,
    onSecondaryContainer = GhostTeal,
    background = GhostBgDark,
    onBackground = GhostTextPrimaryDark,
    surface = GhostSurfaceDark,
    onSurface = GhostTextPrimaryDark,
    surfaceVariant = GhostCardDark,
    onSurfaceVariant = GhostTextSecondaryDark,
    outline = GhostBorderDark,
    error = GhostRed
)

private val LightColorScheme = lightColorScheme(
    primary = GhostSkyDark,
    onPrimary = Color.White,
    primaryContainer = GhostCardLight,
    onPrimaryContainer = GhostSkyDark,
    secondary = GhostTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    background = GhostBgLight,
    onBackground = GhostTextPrimaryLight,
    surface = GhostSurfaceLight,
    onSurface = GhostTextPrimaryLight,
    surfaceVariant = GhostCardLight,
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
