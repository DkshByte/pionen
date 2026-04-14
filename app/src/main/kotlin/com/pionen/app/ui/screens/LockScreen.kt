package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.security.LockState
import com.pionen.app.core.security.UnlockResult
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.LockViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium Lock screen with elegant animations and glassmorphism design.
 * Two-factor authentication: Biometric (Step 1) + 6-digit PIN (Step 2)
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
    
    val biometricAvailable = remember { viewModel.isBiometricAvailable() }
    
    // Screen entrance animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }
    
    // Handle unlock state
    LaunchedEffect(lockState) {
        if (lockState is LockState.Unlocked) {
            onUnlocked()
        }
    }
    
    // Auto-unlock if no security configured
    LaunchedEffect(biometricAvailable, isPinConfigured, biometricPassed) {
        if (biometricPassed && !isPinConfigured) {
            onUnlocked()
        }
        if (!biometricAvailable && !isPinConfigured) {
            onUnlocked()
        }
    }
    
    // Error shake animation
    val shakeOffset by animateFloatAsState(
        targetValue = if (showError) 1f else 0f,
        animationSpec = if (showError) {
            spring(dampingRatio = 0.3f, stiffness = 800f)
        } else {
            tween(0)
        },
        label = "shake"
    )
    
    LaunchedEffect(showError) {
        if (showError) {
            delay(500)
            showError = false
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(600, easing = PionenEasing.EaseOut)) +
                    slideInVertically(
                        animationSpec = tween(600, easing = PionenEasing.EaseOut),
                        initialOffsetY = { it / 10 }
                    ),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val showPinScreen = biometricPassed && isPinConfigured
                
                if (showPinScreen) {
                    // ===== PIN ENTRY SCREEN =====
                    PinEntryContent(
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
                                        val success = viewModel.verifyPin(newPin)
                                        if (!success) {
                                            errorMessage = "Incorrect PIN. Try again."
                                            showError = true
                                            pinInput = ""
                                        }
                                    }
                                }
                            }
                        },
                        onDeleteClick = {
                            if (pinInput.isNotEmpty()) {
                                pinInput = pinInput.dropLast(1)
                                errorMessage = null
                            }
                        }
                    )
                } else {
                    // ===== BIOMETRIC SCREEN =====
                    BiometricContent(
                        isPinConfigured = isPinConfigured,
                        biometricAvailable = biometricAvailable,
                        isAuthenticating = isAuthenticating,
                        errorMessage = errorMessage,
                        failedAttempts = failedAttempts,
                        onAuthenticate = {
                            val activity = context as? FragmentActivity
                            if (activity == null) {
                                errorMessage = "Cannot authenticate"
                                return@BiometricContent
                            }
                            scope.launch {
                                isAuthenticating = true
                                errorMessage = null
                                try {
                                    when (val result = viewModel.authenticateBiometric(activity)) {
                                        is UnlockResult.Success -> {
                                            isAuthenticating = false
                                        }
                                        is UnlockResult.Error -> {
                                            errorMessage = result.message
                                            isAuthenticating = false
                                        }
                                        is UnlockResult.TooManyAttempts -> {
                                            errorMessage = "Too many failed attempts"
                                            isAuthenticating = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Authentication failed"
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

@Composable
private fun BiometricContent(
    isPinConfigured: Boolean,
    biometricAvailable: Boolean,
    isAuthenticating: Boolean,
    errorMessage: String?,
    failedAttempts: Int,
    onAuthenticate: () -> Unit
) {
    // Pulsing glow for lock icon
    val pulseAlpha = rememberPulseAnimation(enabled = true, minAlpha = 0.5f, maxAlpha = 1f)
    
    // Lock icon with glow
    Box(
        modifier = Modifier
            .size(140.dp)
            .graphicsLayer { alpha = pulseAlpha }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VaultGreenGlow,
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DarkCard,
                            DarkSurfaceVariant
                        )
                    )
                )
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.size(48.dp),
                tint = VaultGreen
            )
        }
    }
    
    Spacer(modifier = Modifier.height(40.dp))
    
    Text(
        text = "Pionen",
        style = MaterialTheme.typography.displayMedium,
        color = TextPrimary
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Secure Workspace",
        style = MaterialTheme.typography.titleMedium,
        color = TextSecondary
    )
    
    if (isPinConfigured) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(VaultGreenSubtle.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = VaultGreen
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "2-Factor Authentication",
                style = MaterialTheme.typography.labelMedium,
                color = VaultGreen
            )
        }
    }
    
    Spacer(modifier = Modifier.height(48.dp))
    
    // Error message with animation
    AnimatedVisibility(
        visible = errorMessage != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Text(
            text = errorMessage ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = DestructiveRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
    
    // Biometric button with glow effect
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "buttonScale"
    )
    
    Button(
        onClick = onAuthenticate,
        enabled = !isAuthenticating && biometricAvailable,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(buttonScale),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VaultGreen,
            disabledContainerColor = VaultGreen.copy(alpha = 0.3f)
        )
    ) {
        if (isAuthenticating) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.Black,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPinConfigured) "Step 1: Fingerprint" else "Unlock with Fingerprint",
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    
    if (!biometricAvailable) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Biometric not available on this device",
            style = MaterialTheme.typography.bodySmall,
            color = DestructiveRed
        )
    }
    
    if (failedAttempts > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Failed attempts: $failedAttempts/5",
            style = MaterialTheme.typography.bodySmall,
            color = DestructiveRed
        )
    }
    
    Spacer(modifier = Modifier.height(48.dp))
    
    // Security badge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = VaultGreen
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Hardware-backed encryption",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun PinEntryContent(
    pinInput: String,
    errorMessage: String?,
    shakeOffset: Float,
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Pin,
        contentDescription = null,
        modifier = Modifier.size(40.dp),
        tint = VaultGreen
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "Enter PIN",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary
    )
    
    Text(
        text = "Enter your 6-digit security PIN",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // PIN Dots with spring animation
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer {
            translationX = shakeOffset * 20f * kotlin.math.sin(shakeOffset * 10f * kotlin.math.PI.toFloat())
        }
    ) {
        repeat(6) { index ->
            val isFilled = index < pinInput.length
            val dotScale by animateFloatAsState(
                targetValue = if (isFilled) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.4f,
                    stiffness = 400f
                ),
                label = "dotScale$index"
            )
            val dotColor by animateColorAsState(
                targetValue = if (isFilled) VaultGreen else DarkCard,
                animationSpec = tween(200),
                label = "dotColor$index"
            )
            
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (!isFilled) Modifier.border(1.dp, GlassBorder, CircleShape)
                        else Modifier
                    )
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Error message
    AnimatedVisibility(
        visible = errorMessage != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut()
    ) {
        Text(
            text = errorMessage ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = DestructiveRed,
            textAlign = TextAlign.Center
        )
    }
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Premium PIN Pad
    PremiumPinPad(
        onDigitClick = onDigitClick,
        onDeleteClick = onDeleteClick
    )
}

// PIN pad layout - extracted outside composable to avoid reallocation
private val PIN_PAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "del")
)

@Composable
private fun PremiumPinPad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        for (row in PIN_PAD_ROWS) {
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                for (key in row) {
                    when {
                        key.isEmpty() -> Spacer(modifier = Modifier.size(72.dp))
                        key == "del" -> {
                            PinPadButton(
                                content = {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "Delete",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                isTransparent = true,
                                onClick = onDeleteClick
                            )
                        }
                        else -> {
                            PinPadButton(
                                content = {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                },
                                onClick = { onDigitClick(key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPadButton(
    content: @Composable () -> Unit,
    isTransparent: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "pinButtonScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed && !isTransparent) DarkCardHover else if (isTransparent) Color.Transparent else DarkCard,
        animationSpec = tween(100),
        label = "pinButtonBg"
    )
    
    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (!isTransparent) Modifier.border(1.dp, GlassBorder.copy(alpha = 0.1f), CircleShape)
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
