package com.pionen.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

// ============================================
// PIONEN ANIMATION SYSTEM
// Minimal, elegant, purposeful animations
// ============================================

/**
 * Standard animation durations
 */
object PionenAnimations {
    // Duration constants
    const val INSTANT = 100
    const val FAST = 200
    const val NORMAL = 300
    const val SLOW = 400
    const val DRAMATIC = 600

    // Stagger delays for lists
    const val STAGGER_DELAY = 50
    const val STAGGER_DELAY_LONG = 80
}

/**
 * Premium easing curves for elegant motion
 */
object PionenEasing {
    // Standard ease out for entering elements
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    
    // Ease in for exiting elements
    val EaseIn = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)
    
    // Ease in-out for transitions
    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    
    // Overshoot for playful interactions
    val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

/**
 * Reusable animation specs
 */
object PionenSpecs {
    // Standard enter animation
    fun <T> enterSpec(): TweenSpec<T> = tween(
        durationMillis = PionenAnimations.NORMAL,
        easing = PionenEasing.EaseOut
    )
    
    // Fast enter for micro-interactions
    fun <T> fastEnterSpec(): TweenSpec<T> = tween(
        durationMillis = PionenAnimations.FAST,
        easing = PionenEasing.EaseOut
    )
    
    // Exit animation
    fun <T> exitSpec(): TweenSpec<T> = tween(
        durationMillis = PionenAnimations.FAST,
        easing = PionenEasing.EaseIn
    )
    
    // Spring for bouncy interactions
    fun <T> springSpec(
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        stiffness: Float = Spring.StiffnessMedium
    ): SpringSpec<T> = spring(
        dampingRatio = dampingRatio,
        stiffness = stiffness
    )
    
    // Snappy spring for quick responses
    fun <T> snappySpringSpec(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
}

/**
 * Animated scale modifier for press states
 */
fun Modifier.animatedScale(
    pressed: Boolean,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PionenSpecs.snappySpringSpec(),
        label = "scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Pulsing glow animation for active states
 */
@Composable
fun rememberPulseAnimation(
    enabled: Boolean = true,
    minAlpha: Float = 0.4f,
    maxAlpha: Float = 1f
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = maxAlpha,
        targetValue = if (enabled) minAlpha else maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = PionenEasing.EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return if (enabled) alpha else 1f
}

/**
 * Shimmer animation for loading states
 */
@Composable
fun rememberShimmerProgress(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    return progress
}

/**
 * Staggered delay calculator for list animations
 */
fun staggeredDelay(index: Int, baseDelay: Int = PionenAnimations.STAGGER_DELAY): Int {
    return index * baseDelay
}

/**
 * Enter transition spec for navigating screens
 */
fun <T> screenEnterSpec(): TweenSpec<T> = tween(
    durationMillis = PionenAnimations.NORMAL,
    easing = PionenEasing.EaseOut
)

/**
 * Exit transition spec for navigating screens
 */
fun <T> screenExitSpec(): TweenSpec<T> = tween(
    durationMillis = PionenAnimations.FAST,
    easing = PionenEasing.EaseIn
)
