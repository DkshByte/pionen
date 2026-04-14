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
// PIONEN THEME — Neon Green + Liquid Glass
// ============================================

private val PionenDarkColorScheme = darkColorScheme(
    // Primary — Neon Green
    primary              = NeonGreen,
    onPrimary            = Color.Black,
    primaryContainer     = NeonGreenDark,
    onPrimaryContainer   = NeonGreenLight,

    // Secondary — Electric Cyan
    secondary            = ElectricCyan,
    onSecondary          = Color.Black,
    secondaryContainer   = Color(0xFF003D4D),
    onSecondaryContainer = Color(0xFFB8F0FF),

    // Tertiary — Neon Purple
    tertiary             = NeonPurple,
    onTertiary           = Color.Black,
    tertiaryContainer    = NeonPurpleDark,
    onTertiaryContainer  = Color(0xFFE8D8FF),

    // Backgrounds
    background           = DarkBackground,
    onBackground         = TextPrimary,

    // Surfaces
    surface              = DarkSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = TextSecondary,
    surfaceTint          = NeonGreen.copy(alpha = 0.03f),

    // Status
    error                = DestructiveRed,
    onError              = Color.White,
    errorContainer       = Color(0xFF4A0E0E),
    onErrorContainer     = Color(0xFFFFB4A2),

    // Outlines
    outline              = TextTertiary,
    outlineVariant       = TextMuted,

    // Inverse
    inverseSurface       = TextPrimary,
    inverseOnSurface     = DarkBackground,
    inversePrimary       = NeonGreenDark,

    // Scrim
    scrim                = Color.Black.copy(alpha = 0.7f)
)

private val PionenLightColorScheme = lightColorScheme(
    // Primary — Neon Green (toned down for light)
    primary              = NeonGreenDark,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFB8FFD6),
    onPrimaryContainer   = Color(0xFF003D1A),

    // Secondary — Electric Cyan
    secondary            = ElectricCyanDark,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFD0F5FF),
    onSecondaryContainer = Color(0xFF003544),

    // Tertiary — Neon Purple
    tertiary             = NeonPurpleDark,
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
    surfaceTint          = NeonGreenDark.copy(alpha = 0.03f),

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
    inversePrimary       = NeonGreenLight,

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
            // Navigation bar — pixel style: pure black to blend with nav bar
            window.navigationBarColor = if (darkTheme) {
                PixelBarBackground.toArgb()
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
