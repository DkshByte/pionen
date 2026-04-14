package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.security.PanicState
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.PanicViewModel
import kotlinx.coroutines.delay

/**
 * Premium Panic confirmation screen with dramatic animations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanicConfirmScreen(
    onCancel: () -> Unit,
    onWipeComplete: () -> Unit,
    viewModel: PanicViewModel = hiltViewModel()
) {
    val panicState by viewModel.panicState.collectAsState()
    var countdown by remember { mutableStateOf(5) }
    var isConfirming by remember { mutableStateOf(false) }
    
    // Screen entrance
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    LaunchedEffect(isConfirming) {
        if (isConfirming) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            viewModel.executePanicWipe()
        }
    }
    
    LaunchedEffect(panicState) {
        if (panicState is PanicState.Complete) {
            delay(1500)
            onWipeComplete()
        }
    }
    
    // Pulsing animation for warning
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = PionenEasing.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val state = panicState) {
                    is PanicState.Ready, is PanicState.Confirming -> {
                        // Warning icon with pulse
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(if (isConfirming) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(DestructiveRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = DestructiveRed
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "Emergency Wipe",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DestructiveRed
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "All encryption keys will be destroyed.\nAll files will become irrecoverable.\nThis action CANNOT be undone.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = TextSecondary
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        AnimatedContent(
                            targetState = isConfirming,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                            },
                            label = "confirmContent"
                        ) { confirming ->
                            if (confirming) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = countdown.toString(),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = DestructiveRed
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "Tap cancel to abort",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    OutlinedButton(
                                        onClick = {
                                            isConfirming = false
                                            countdown = 5
                                            onCancel()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TextPrimary
                                        )
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Button(
                                        onClick = { isConfirming = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DestructiveRed
                                        )
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Wipe Everything", color = Color.White)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    OutlinedButton(
                                        onClick = onCancel,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Cancel", color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    
                    is PanicState.Wiping -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(80.dp),
                            color = DestructiveRed,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Destroying all keys...",
                            style = MaterialTheme.typography.titleLarge,
                            color = DestructiveRed
                        )
                    }
                    
                    is PanicState.Complete -> {
                        val checkScale by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 200f),
                            label = "checkScale"
                        )
                        
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .scale(checkScale),
                            tint = DestructiveRed
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Wipe Complete",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${state.result.keysDestroyed} keys destroyed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "All files are now irrecoverable",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    
                    is PanicState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = DestructiveRed
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Wipe Failed",
                            style = MaterialTheme.typography.titleLarge,
                            color = DestructiveRed
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
