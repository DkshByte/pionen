package com.pionen.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// PIONEN THEME — Cobalt Blue + Full Dark/Light
// ============================================

private val PionenDarkColorScheme = darkColorScheme(
    // Primary — Cobalt Blue (logo brand color)
    primary              = PionenBlueLight,     // Lighter on dark for readability
    onPrimary            = Color.White,
    primaryContainer     = PionenBlueDark,
    onPrimaryContainer   = Color(0xFFD0D0FF),

    // Secondary — Electric Blue
    secondary            = PionenElectric,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF1A2A5E),
    onSecondaryContainer = Color(0xFFB8D0FF),

    // Tertiary — Violet
    tertiary             = PionenViolet,
    onTertiary           = Color.White,
    tertiaryContainer    = PionenVioletDark,
    onTertiaryContainer  = Color(0xFFDDD0FF),

    // Backgrounds
    background           = DarkBackground,
    onBackground         = TextPrimary,

    // Surfaces
    surface              = DarkSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = TextSecondary,
    surfaceTint          = PionenBlueLight.copy(alpha = 0.05f),

    // Status
    error                = DestructiveRed,
    onError              = Color.White,
    errorContainer       = Color(0xFF4A0E0E),
    onErrorContainer     = Color(0xFFFFB4A2),

    // Outlines
    outline              = TextTertiary,
    outlineVariant       = TextMuted,

    // Inverse (snackbars, etc.)
    inverseSurface       = TextPrimary,
    inverseOnSurface     = DarkBackground,
    inversePrimary       = PionenBlueDark,

    // Scrim
    scrim                = Color.Black.copy(alpha = 0.6f)
)

private val PionenLightColorScheme = lightColorScheme(
    // Primary — Cobalt Blue (full brand strength on white)
    primary              = PionenBlue,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFDDDDFF),
    onPrimaryContainer   = Color(0xFF0A0870),

    // Secondary — Electric Blue
    secondary            = PionenElectricDark,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFD8E8FF),
    onSecondaryContainer = Color(0xFF0A2060),

    // Tertiary — Violet
    tertiary             = PionenVioletDark,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFE8D8FF),
    onTertiaryContainer  = Color(0xFF2A0860),

    // Backgrounds
    background           = LightBackground,
    onBackground         = LightTextPrimary,

    // Surfaces
    surface              = LightSurface,
    onSurface            = LightTextPrimary,
    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = LightTextSecondary,
    surfaceTint          = PionenBlue.copy(alpha = 0.03f),

    // Status
    error                = DestructiveRedDark,
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),

    // Outlines
    outline              = LightTextTertiary,
    outlineVariant       = LightTextMuted,

    // Inverse
    inverseSurface       = LightTextPrimary,
    inverseOnSurface     = LightSurface,
    inversePrimary       = PionenBlueLight,

    // Scrim
    scrim                = Color.Black.copy(alpha = 0.4f)
)

@Composable
fun PionenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // Keep consistent branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PionenDarkColorScheme
        else      -> PionenLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent status bar for edge-to-edge
            window.statusBarColor = Color.Transparent.toArgb()
            // Navigation bar matches theme background
            window.navigationBarColor = if (darkTheme) {
                DarkBackground.toArgb()
            } else {
                LightBackground.toArgb()
            }
            // Adapt icon brightness to theme
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
