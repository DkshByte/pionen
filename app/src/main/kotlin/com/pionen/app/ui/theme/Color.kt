package com.pionen.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// PIONEN BRAND COLORS — Cobalt Blue Identity
// Extracted from official Pionen logo (#2D2BCE)
// ============================================

// Primary brand — Cobalt Blue (matches logo exactly)
val PionenBlue         = Color(0xFF2D2BCE)   // Brand primary (logo color)
val PionenBlueDark     = Color(0xFF1F1DA0)   // Darker variant for containers
val PionenBlueLight    = Color(0xFF5654E8)   // Lighter variant for dark surfaces
val PionenBlueMuted    = Color(0x402D2BCE)   // 25% opacity glow

// Secondary accent — Electric blue (lighter, energetic)
val PionenElectric     = Color(0xFF4F8EFF)   // Bright interactive blue
val PionenElectricDark = Color(0xFF3B6FE0)   // Pressed / dark variant

// Tertiary accent — Violet
val PionenViolet       = Color(0xFF7C5CFC)   // Violet for highlights
val PionenVioletDark   = Color(0xFF5E3FD8)   // Dark variant

// Status colors
val DestructiveRed     = Color(0xFFF87171)   // Soft red, less harsh
val DestructiveRedDark = Color(0xFFEF4444)   // Deeper red for buttons
val WarningOrange      = Color(0xFFFBBF24)   // Warm amber
val SafeGreen          = Color(0xFF34D399)   // Success green

// ============================================
// DARK THEME SURFACE SYSTEM
// Deep navy-black palette — security aesthetic
// ============================================

val DarkBackground        = Color(0xFF080810)   // Deep navy-black base
val DarkSurface           = Color(0xFF0E0E1A)   // First elevation
val DarkSurfaceVariant    = Color(0xFF15151F)   // Second elevation (cards bg)
val DarkCard              = Color(0xFF1C1C2A)   // Elevated card
val DarkCardHover         = Color(0xFF232333)   // Card pressed/hover state

// Glassmorphism overlays
val GlassSurface          = Color(0x1AFFFFFF)   // 10% white
val GlassSurfaceHover     = Color(0x26FFFFFF)   // 15% white
val GlassBorder           = Color(0x33FFFFFF)   // 20% white border
val GlassBorderSubtle     = Color(0x1AFFFFFF)   // 10% white border

// Blue-tinted glassmorphism (branded)
val BlueSurface           = Color(0x152D2BCE)   // 8% brand blue overlay
val BlueBorder            = Color(0x402D2BCE)   // 25% brand blue border

// ============================================
// LIGHT THEME SURFACE SYSTEM
// Clean white — minimal, open aesthetic
// ============================================

val LightBackground       = Color(0xFFF5F5FA)   // Soft off-white (not harsh)
val LightSurface          = Color(0xFFFFFFFF)   // Pure white surface
val LightSurfaceVariant   = Color(0xFFEEEEF8)   // Slight blue-tint variant
val LightCard             = Color(0xFFFFFFFF)   // Card white
val LightCardHover        = Color(0xFFF0F0FF)   // Card hover — blue tint

// ============================================
// TEXT COLORS — Shared across themes
// ============================================

// Dark theme text
val TextPrimary    = Color(0xFFF0F0FF)   // Near-white with blue tint
val TextSecondary  = Color(0xFFA0A0C0)   // Muted blue-grey
val TextTertiary   = Color(0xFF6060A0)   // Dimmer
val TextMuted      = Color(0xFF404068)   // Very dim

// Light theme text
val LightTextPrimary   = Color(0xFF0A0A1A)   // Near-black with blue tint
val LightTextSecondary = Color(0xFF4A4A7A)   // Mid blue-grey
val LightTextTertiary  = Color(0xFF7070A8)   // Lighter
val LightTextMuted     = Color(0xFFA0A0C8)   // Very light

// ============================================
// SPECIAL EFFECTS
// ============================================

// Gradient presets
val GradientBrandStart = Color(0xFF2D2BCE)   // Brand blue
val GradientBrandEnd   = Color(0xFF7C5CFC)   // Violet
val GradientCyanEnd    = Color(0xFF00C6FF)   // Cyan for shimmer

// Shimmer / skeleton loading
val ShimmerBase        = Color(0xFF1C1C2A)
val ShimmerHighlight   = Color(0xFF2D2D42)

// Floating bar — glassmorphism
val FloatingBarBackground  = Color(0xE6100F1E)   // 90% dark navy
val FloatingBarBorder      = Color(0x402D2BCE)   // 25% brand blue border
val ActiveChipBackground   = Color(0x332D2BCE)   // Brand blue at 20%

// Incognito / browser theme
val IncognitoBackground = Color(0xFF0D0D1A)
val IncognitoSurface    = Color(0xFF181828)
val IncognitoAccent     = Color(0xFF4F8EFF)

// ============================================
// LEGACY ALIASES — Maintain backward compat
// Code that still references VaultGreen will
// now automatically use PionenBlue.
// ============================================

val VaultGreen        = PionenBlue
val VaultGreenDark    = PionenBlueDark
val VaultGreenSubtle  = Color(0xFF12124A)   // Very dark blue (replaces green subtle)
val VaultGreenGlow    = PionenBlueMuted
val SecureBlue        = PionenElectric
val SecureBlueDark    = PionenElectricDark
val AccentPurple      = PionenViolet
val AccentPurpleDark  = PionenVioletDark
