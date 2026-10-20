package com.generic.mnemos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Warm-red dark scheme — the app's default and primary (and intended-only) experience.
private val MnemosDarkColors = darkColorScheme(
    primary = InkAccent,
    onPrimary = InkAccentOn,
    primaryContainer = InkAccentGlow,
    onPrimaryContainer = InkAccentLight,
    secondary = InkAccentLight,
    background = InkBackground,
    onBackground = InkTextPrimary,
    surface = InkSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = InkSurface1,
    onSurfaceVariant = InkTextSecondary,
    surfaceContainerHighest = InkSurface1,
    outline = InkBorder,
    outlineVariant = InkBorder,
    error = InkError,
)

// Light scheme is a secondary path; kept tasteful and warm. The brand lives in the dark.
private val MnemosLightColors = lightColorScheme(
    primary = InkAccent,
    onPrimary = InkWhite,
    background = Color(0xFFF7F1F1),
    surface = Color(0xFFFFFBFB),
    error = InkError,
)

@Composable
fun MnemosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // The brand IS the warm-red palette, so dynamic (wallpaper-driven) colour is OFF by
    // default — we want identical, deliberate colour on every device.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> MnemosDarkColors
        else -> MnemosLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // enableEdgeToEdge() (in MainActivity) owns the bar scrim; here we only set
            // the status-bar icon contrast to match the theme. Setting statusBarColor
            // directly is deprecated on API 35+, so we don't.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MnemosTypography,
        shapes = MnemosShapes,
        content = content,
    )
}
