package com.pionen.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================
// PIXEL-ART DESIGN TOKENS
// Crisp edges · chunky borders · retro feel
// ============================================

object PixelUI {
    // All corners are SQUARE (0dp) or very slightly rounded (2dp) — pixel style!
    val cornerNone: Dp = 0.dp
    val cornerTiny: Dp = 2.dp
    val cornerSmall: Dp = 4.dp
    val cornerMedium: Dp = 6.dp
    val cornerLarge: Dp = 8.dp

    // Border widths — pixel style uses bold 2px borders
    val borderThin: Dp = 1.dp
    val borderNormal: Dp = 2.dp
    val borderThick: Dp = 3.dp

    // Padding system (multiples of 4 for pixel grid alignment)
    val padXS: Dp = 4.dp
    val padS: Dp = 8.dp
    val padM: Dp = 12.dp
    val padL: Dp = 16.dp
    val padXL: Dp = 24.dp
    val padXXL: Dp = 32.dp

    // Pixel "shadow" offsets — bottom-right solid shadow
    val shadowOffset: Dp = 3.dp
    val shadowOffsetSmall: Dp = 2.dp

    // Icon sizes
    val iconXS: Dp = 14.dp
    val iconS: Dp = 18.dp
    val iconM: Dp = 24.dp
    val iconL: Dp = 32.dp
    val iconXL: Dp = 48.dp

    // Debug grid unit: 4dp
    const val GRID = 4
}
