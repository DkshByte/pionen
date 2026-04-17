package com.pionen.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// PIONEN PIXEL-ART GEN-Z COLOR SYSTEM
// Dark canvas · neon accents · pixel crisp
// ============================================

// ─── PRIMARY — Pixel Neon Green ───────────────
val NeonGreen            = Color(0xFF00FF87)   // Primary neon green
val NeonGreenDark        = Color(0xFF00CC6A)   // Darker shade
val NeonGreenLight       = Color(0xFF66FFB2)   // Lighter tint
val NeonGreenMuted       = Color(0x4000FF87)   // 25% opacity glow
val NeonGreenSubtle      = Color(0x1A00FF87)   // 10% background tint

// ─── SECONDARY — Electric Cyan ───────────────
val ElectricCyan         = Color(0xFF00E5FF)
val ElectricCyanDark     = Color(0xFF00B8D4)

// ─── TERTIARY — Neon Purple ──────────────────
val NeonPurple           = Color(0xFFBB86FC)
val NeonPurpleDark       = Color(0xFF9C5CFC)

// ─── STATUS ──────────────────────────────────
val DestructiveRed       = Color(0xFFF87171)
val DestructiveRedDark   = Color(0xFFEF4444)
val WarningOrange        = Color(0xFFFBBF24)
val SafeGreen            = Color(0xFF34D399)

// ─── PIXEL DARK SURFACES ─────────────────────
// True pixel-art style: very deep blacks with sharp steps
val DarkBackground        = Color(0xFF080808)   // Near pure black
val DarkSurface           = Color(0xFF0F0F0F)   // Step 1
val DarkSurfaceVariant    = Color(0xFF161616)   // Step 2
val DarkCard              = Color(0xFF1A1A1A)   // Card surface
val DarkCardHover         = Color(0xFF212121)   // Pressed card

// ─── PIXEL BORDER SYSTEM ─────────────────────
// Sharp 1px or 2px borders like classic pixel UIs
val PixelBorderBright     = Color(0xFF2A2A2A)   // Inner light border
val PixelBorderDark       = Color(0xFF050505)   // Outer dark border (depth)
val PixelBorderNeon       = Color(0xFF00FF87)   // Neon green 1px border
val PixelBorderNeonFaint  = Color(0x3300FF87)   // Neon at 20%

// ─── LIQUID GLASS SYSTEM (for overlays only) ─
val LiquidGlass           = Color(0x0DFFFFFF)
val LiquidGlassLight      = Color(0x1AFFFFFF)
val LiquidGlassMedium     = Color(0x26FFFFFF)
val LiquidGlassHeavy      = Color(0x33FFFFFF)

val GlassBorder           = Color(0x33FFFFFF)
val GlassBorderSubtle     = Color(0x1AFFFFFF)
val GlassBorderBright     = Color(0x4DFFFFFF)

val NeonGlassSurface      = Color(0x0D00FF87)
val NeonGlassBorder       = Color(0x3300FF87)
val NeonGlassGlow         = Color(0x2600FF87)

// ─── LIGHT THEME (minimal) ───────────────────
val LightBackground       = Color(0xFFF5F5FA)
val LightSurface          = Color(0xFFFFFFFF)
val LightSurfaceVariant   = Color(0xFFEEEEF8)
val LightCard             = Color(0xFFFFFFFF)
val LightCardHover        = Color(0xFFF0F0FF)

// ─── TEXT ────────────────────────────────────
val TextPrimary    = Color(0xFFF0F0F0)
val TextSecondary  = Color(0xFF888888)
val TextTertiary   = Color(0xFF505050)
val TextMuted      = Color(0xFF303030)

val LightTextPrimary   = Color(0xFF0A0A1A)
val LightTextSecondary = Color(0xFF4A4A7A)
val LightTextTertiary  = Color(0xFF7070A8)
val LightTextMuted     = Color(0xFFA0A0C8)

// ─── PIXEL GRADIENT PRESETS ──────────────────
val GradientNeonStart   = Color(0xFF00FF87)
val GradientNeonEnd     = Color(0xFF00E5FF)
val GradientPurpleEnd   = Color(0xFFBB86FC)
val GradientDarkStart   = Color(0xFF0A0A0A)
val GradientDarkEnd     = Color(0xFF1A1A1A)

// Shimmer / skeleton loading
val ShimmerBase        = Color(0xFF161616)
val ShimmerHighlight   = Color(0xFF2A2A2A)

// ─── BOTTOM BAR ──────────────────────────────
val PixelBarBackground  = Color(0xF0080808)   // 94% dark
val PixelBarBorder      = Color(0xFF00FF87)   // Neon green top border (pixel style)
val ActiveChipBackground= Color(0x2600FF87)

// ─── BROWSER / INCOGNITO ─────────────────────
val IncognitoBackground = Color(0xFF0A0A0A)
val IncognitoSurface    = Color(0xFF141414)
val IncognitoAccent     = Color(0xFF00E5FF)

// ─── ALIASES (used across multiple screens) ──
val VaultGreen        = NeonGreen
val VaultGreenDark    = NeonGreenDark
val VaultGreenSubtle  = Color(0xFF0A1A12)
val VaultGreenGlow    = NeonGreenMuted
val AccentPurple      = NeonPurple
val AccentPurpleDark  = NeonPurpleDark
val SecureBlue        = ElectricCyan
val SecureBlueDark    = ElectricCyanDark
