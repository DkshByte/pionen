package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.security.UnlockResult
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.LockViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pixel-art Gen-Z Lock Screen.
 * Deep black background · neon green pixel accents · retro grid widget.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lockState by viewModel.lockState.collectAsState()
    val failedAttempts by viewModel.failedAttempts.collectAsState()
    val biometricPassed by viewModel.biometricPassed.collectAsState()
    val isPinConfigured by viewModel.isPinConfigured.collectAsState()

    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }

    val biometricAvailable = remember { viewModel.isBiometricAvailable() }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        isVisible = true
    }

    LaunchedEffect(biometricAvailable, isPinConfigured, biometricPassed) {
        if (!hasNavigated) {
            if (biometricPassed && !isPinConfigured) { hasNavigated = true; onUnlocked() }
            if (!biometricAvailable && !isPinConfigured) { hasNavigated = true; onUnlocked() }
        }
    }

    val shakeOffset by animateFloatAsState(
        targetValue = if (showError) 1f else 0f,
        animationSpec = if (showError) spring(dampingRatio = 0.3f, stiffness = 800f) else tween(0),
        label = "shake"
    )
    LaunchedEffect(showError) { if (showError) { delay(500); showError = false } }

    // Scanline flicker
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanlineY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Pixel scanline effect — subtle horizontal line that scrolls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer { translationY = scanY * 2400f - 32f }
                .background(NeonGreen.copy(alpha = 0.04f))
        )

        // Corner pixel decorations
        PixelCornerDecor()

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(400)) +
                    slideInVertically(animationSpec = tween(400, easing = PionenEasing.EaseOut), initialOffsetY = { it / 8 }),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val showPinScreen = biometricPassed && isPinConfigured

                if (showPinScreen) {
                    PixelPinEntryContent(
                        pinInput = pinInput,
                        errorMessage = errorMessage,
                        shakeOffset = shakeOffset,
                        onDigitClick = { digit ->
                            if (pinInput.length < 6) {
                                val newPin = pinInput + digit
                                pinInput = newPin
                                errorMessage = null
                                if (newPin.length == 6) {
                                    scope.launch {
                                        try {
                                            val success = viewModel.verifyPin(newPin)
                                            if (success) {
                                                if (!hasNavigated) { hasNavigated = true; onUnlocked() }
                                            } else {
                                                errorMessage = "WRONG PIN — TRY AGAIN"
                                                showError = true
                                                pinInput = ""
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "AUTH ERROR"
                                            pinInput = ""
                                        }
                                    }
                                }
                            }
                        },
                        onDeleteClick = {
                            if (pinInput.isNotEmpty()) { pinInput = pinInput.dropLast(1); errorMessage = null }
                        }
                    )
                } else {
                    PixelBiometricContent(
                        isPinConfigured = isPinConfigured,
                        biometricAvailable = biometricAvailable,
                        isAuthenticating = isAuthenticating,
                        errorMessage = errorMessage,
                        failedAttempts = failedAttempts,
                        onAuthenticate = {
                            val activity = context as? FragmentActivity ?: run { errorMessage = "Cannot authenticate"; return@PixelBiometricContent }
                            scope.launch {
                                isAuthenticating = true
                                errorMessage = null
                                try {
                                    when (val result = viewModel.authenticateBiometric(activity)) {
                                        is UnlockResult.Success -> isAuthenticating = false
                                        is UnlockResult.Error -> { errorMessage = result.message; isAuthenticating = false }
                                        is UnlockResult.TooManyAttempts -> { errorMessage = "TOO MANY ATTEMPTS"; isAuthenticating = false }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "AUTH FAILED"
                                    isAuthenticating = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────
// PIXEL CORNER DECORATIONS
// ────────────────────────────────────────────────
@Composable
private fun PixelCornerDecor() {
    val cornerColor = NeonGreen.copy(alpha = 0.25f)
    val cornerSize = 20.dp
    val borderWidth = 2.dp

    // Top-left
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.TopStart)
                .padding(12.dp)
                .drawBehind {
                    drawLine(cornerColor, Offset(0f, size.height), Offset(0f, 0f), strokeWidth = borderWidth.toPx())
                    drawLine(cornerColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = borderWidth.toPx())
                }
        )
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .drawBehind {
                    drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width, 0f), strokeWidth = borderWidth.toPx())
                    drawLine(cornerColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = borderWidth.toPx())
                }
        )
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .drawBehind {
                    drawLine(cornerColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = borderWidth.toPx())
                    drawLine(cornerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = borderWidth.toPx())
                }
        )
        Box(
            Modifier
                .size(cornerSize)
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .drawBehind {
                    drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = borderWidth.toPx())
                    drawLine(cornerColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = borderWidth.toPx())
                }
        )
    }
}

// ────────────────────────────────────────────────
// BIOMETRIC CONTENT — Pixel Lock Icon + Button
// ────────────────────────────────────────────────
@Composable
private fun PixelBiometricContent(
    isPinConfigured: Boolean,
    biometricAvailable: Boolean,
    isAuthenticating: Boolean,
    errorMessage: String?,
    failedAttempts: Int,
    onAuthenticate: () -> Unit
) {
    val pulseAlpha = rememberPulseAnimation(enabled = true, minAlpha = 0.6f, maxAlpha = 1f)

    // ── Pixel shield icon ──
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer { alpha = pulseAlpha }
            // Outer pixel shadow (offset, solid, pixel-style)
            .drawBehind {
                drawRect(
                    color = NeonGreen.copy(alpha = 0.15f),
                    topLeft = Offset(4f, 4f),
                    size = size
                )
            }
            .background(DarkCard)
            .border(2.dp, NeonGreen, RoundedCornerShape(0.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Locked",
            modifier = Modifier.size(48.dp),
            tint = NeonGreen
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    // App title — pixel / monospace feel
    Text(
        text = "PIONEN",
        style = MaterialTheme.typography.displaySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp
        ),
        color = NeonGreen
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "SECURE VAULT",
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        ),
        color = TextSecondary
    )

    if (isPinConfigured) {
        Spacer(modifier = Modifier.height(8.dp))
        PixelBadge(text = "[ 2FA ACTIVE ]", color = NeonGreen)
    }

    Spacer(modifier = Modifier.height(40.dp))

    // Error message
    AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = "> ${errorMessage ?: ""}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = DestructiveRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    // Fingerprint button — pixel style wide button
    PixelButton(
        onClick = onAuthenticate,
        enabled = !isAuthenticating && biometricAvailable,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isAuthenticating) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.Black,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("AUTHENTICATING...", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Black)
        } else {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.Black
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isPinConfigured) "[ SCAN FINGERPRINT ]" else "[ UNLOCK ]",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }

    if (!biometricAvailable) {
        Spacer(Modifier.height(10.dp))
        PixelBadge(text = "BIOMETRIC UNAVAILABLE", color = DestructiveRed)
    }

    if (failedAttempts > 0) {
        Spacer(Modifier.height(12.dp))
        PixelBadge(text = "FAILED: $failedAttempts/5", color = WarningOrange)
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Security badge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, PixelBorderNeonFaint, RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = NeonGreen)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "AES-256 · HARDWARE BACKED",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = TextTertiary
        )
    }
}

// ────────────────────────────────────────────────
// PIN ENTRY CONTENT
// ────────────────────────────────────────────────
@Composable
private fun PixelPinEntryContent(
    pinInput: String,
    errorMessage: String?,
    shakeOffset: Float,
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Pin,
        contentDescription = null,
        modifier = Modifier.size(36.dp),
        tint = NeonGreen
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = "ENTER PIN",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        ),
        color = TextPrimary
    )

    Text(
        text = "step 2 of 2",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = TextSecondary
    )

    Spacer(Modifier.height(28.dp))

    // PIN Dots
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            translationX = shakeOffset * 18f * kotlin.math.sin(shakeOffset * 10f * kotlin.math.PI.toFloat())
        }
    ) {
        repeat(6) { index ->
            val isFilled = index < pinInput.length
            val dotColor by animateColorAsState(
                targetValue = if (isFilled) NeonGreen else DarkCard,
                animationSpec = tween(150),
                label = "dotColor$index"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isFilled) NeonGreen else PixelBorderBright,
                animationSpec = tween(150),
                label = "dotBorder$index"
            )
            // Square pixel dots
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(14.dp)
                    .background(dotColor)
                    .border(1.dp, borderColor)
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = "> ${errorMessage ?: ""}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = DestructiveRed,
            textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(28.dp))

    PixelPinPad(onDigitClick = onDigitClick, onDeleteClick = onDeleteClick)
}

// ────────────────────────────────────────────────
// PIXEL PIN PAD
// ────────────────────────────────────────────────
private val PIN_PAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "del")
)

@Composable
private fun PixelPinPad(onDigitClick: (String) -> Unit, onDeleteClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in PIN_PAD_ROWS) {
            Row(
                modifier = Modifier.padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (key in row) {
                    when {
                        key.isEmpty() -> Spacer(Modifier.size(68.dp))
                        key == "del" -> {
                            PixelKeyButton(isTransparent = false, onClick = onDeleteClick) {
                                Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                        else -> {
                            PixelKeyButton(onClick = { onDigitClick(key) }) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PixelKeyButton(
    isTransparent: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isTransparent -> Color.Transparent
            isPressed -> DarkCardHover
            else -> DarkCard
        },
        animationSpec = tween(80),
        label = "keyBg"
    )

    Box(
        modifier = Modifier
            .size(68.dp)
            // Pixel shadow effect: offset box beneath
            .drawBehind {
                if (!isTransparent && !isPressed) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(3f, 3f),
                        size = size
                    )
                }
            }
            .background(bgColor)
            .then(
                if (!isTransparent) Modifier.border(1.dp, PixelBorderBright)
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ────────────────────────────────────────────────
// SHARED PIXEL COMPONENTS
// ────────────────────────────────────────────────

@Composable
fun PixelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(52.dp)
            .drawBehind {
                if (!isPressed && enabled) {
                    drawRect(
                        color = NeonGreenDark,
                        topLeft = Offset(3f, 3f),
                        size = size
                    )
                }
            }
            .background(if (enabled) NeonGreen else NeonGreen.copy(alpha = 0.3f))
            .border(1.dp, if (enabled) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun PixelBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = color
        )
    }
}
