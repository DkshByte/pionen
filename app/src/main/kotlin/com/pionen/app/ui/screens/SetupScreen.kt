package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.ui.components.*
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.SetupViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SetupStep {
    WELCOME, CREATE_PIN, CONFIRM_PIN
}

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }
    
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        isVisible = true
    }

    val shakeOffset by animateFloatAsState(
        targetValue = if (showError) 1f else 0f,
        animationSpec = if (showError) spring(dampingRatio = 0.3f, stiffness = 800f) else tween(0),
        label = "shake"
    )
    LaunchedEffect(showError) { if (showError) { delay(500); showError = false } }

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
        // Pixel scanline effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer { translationY = scanY * 2400f - 32f }
                .background(NeonGreen.copy(alpha = 0.04f))
        )

        PixelCornerDecor()

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { it / 8 }),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Crossfade(targetState = currentStep, label = "SetupStepFade") { step ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (step) {
                            SetupStep.WELCOME -> WelcomeContent {
                                currentStep = SetupStep.CREATE_PIN
                            }
                            SetupStep.CREATE_PIN -> SetupPinContent(
                                title = "CREATE PIN",
                                subtitle = "step 1 of 2",
                                pinInput = firstPin,
                                errorMessage = errorMessage,
                                shakeOffset = shakeOffset,
                                onDigitClick = { digit ->
                                    if (firstPin.length < 6) {
                                        firstPin += digit
                                        errorMessage = null
                                        if (firstPin.length == 6) {
                                            currentStep = SetupStep.CONFIRM_PIN
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                                }
                            )
                            SetupStep.CONFIRM_PIN -> SetupPinContent(
                                title = "CONFIRM PIN",
                                subtitle = "step 2 of 2",
                                pinInput = confirmPin,
                                errorMessage = errorMessage,
                                shakeOffset = shakeOffset,
                                onDigitClick = { digit ->
                                    if (confirmPin.length < 6) {
                                        confirmPin += digit
                                        errorMessage = null
                                        if (confirmPin.length == 6) {
                                            if (firstPin == confirmPin) {
                                                isProcessing = true
                                                viewModel.setupPin(confirmPin) {
                                                    onSetupComplete()
                                                }
                                            } else {
                                                errorMessage = "PIN MISMATCH — RESTART"
                                                showError = true
                                                firstPin = ""
                                                confirmPin = ""
                                                scope.launch {
                                                    delay(600)
                                                    currentStep = SetupStep.CREATE_PIN
                                                }
                                            }
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeContent(onNext: () -> Unit) {
    val pulseAlpha = rememberPulseAnimation(enabled = true, minAlpha = 0.6f, maxAlpha = 1f)

    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer { alpha = pulseAlpha }
            .drawBehind { drawRect(color = NeonGreen.copy(alpha = 0.15f), topLeft = Offset(4f, 4f), size = size) }
            .background(DarkCard)
            .border(2.dp, NeonGreen, RoundedCornerShape(0.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Security, contentDescription = "Security", modifier = Modifier.size(48.dp), tint = NeonGreen)
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "INITIALIZE VAULT",
        style = MaterialTheme.typography.displaySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        ),
        color = NeonGreen,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "YOUR DEVICE WILL NOW BE SANDBOXED.\nSECURE YOUR VAULT WITH A 6-DIGIT PIN.\n\nFINGERPRINT LOGIN IS OPTIONAL LATER.",
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            lineHeight = 24.sp
        ),
        color = TextSecondary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(40.dp))

    PixelButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "[ PROCEED TO PIN SETUP ]",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
private fun SetupPinContent(
    title: String,
    subtitle: String,
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
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        ),
        color = TextPrimary
    )

    Text(
        text = subtitle,
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
