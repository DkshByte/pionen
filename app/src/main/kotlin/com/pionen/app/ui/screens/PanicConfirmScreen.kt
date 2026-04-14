package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.security.PanicState
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.PanicViewModel
import kotlinx.coroutines.delay

/**
 * Pixel-art Panic / Emergency Wipe Screen.
 * High-contrast red danger UI with pixel aesthetic.
 */
@Composable
fun PanicConfirmScreen(
    onCancel: () -> Unit,
    onWipeComplete: () -> Unit,
    viewModel: PanicViewModel = hiltViewModel()
) {
    val panicState by viewModel.panicState.collectAsState()
    var countdown by remember { mutableStateOf(5) }
    var isConfirming by remember { mutableStateOf(false) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    LaunchedEffect(isConfirming) {
        if (isConfirming) {
            while (countdown > 0) { delay(1000); countdown-- }
            viewModel.executePanicWipe()
        }
    }

    LaunchedEffect(panicState) {
        if (panicState is PanicState.Complete) { delay(1500); onWipeComplete() }
    }

    // Pixel flicker/strobe on confirm
    val infiniteTransition = rememberInfiniteTransition(label = "panicPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConfirming) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "panicAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Corner pixel decorations in red
        Box(Modifier.fillMaxSize()) {
            val rc = DestructiveRed.copy(alpha = 0.4f)
            Box(Modifier.size(28.dp).align(Alignment.TopStart).padding(10.dp).drawBehind {
                drawLine(rc, Offset(0f, size.height), Offset(0f, 0f), 2f)
                drawLine(rc, Offset(0f, 0f), Offset(size.width, 0f), 2f)
            })
            Box(Modifier.size(28.dp).align(Alignment.TopEnd).padding(10.dp).drawBehind {
                drawLine(rc, Offset(size.width, size.height), Offset(size.width, 0f), 2f)
                drawLine(rc, Offset(0f, 0f), Offset(size.width, 0f), 2f)
            })
            Box(Modifier.size(28.dp).align(Alignment.BottomStart).padding(10.dp).drawBehind {
                drawLine(rc, Offset(0f, 0f), Offset(0f, size.height), 2f)
                drawLine(rc, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            })
            Box(Modifier.size(28.dp).align(Alignment.BottomEnd).padding(10.dp).drawBehind {
                drawLine(rc, Offset(size.width, 0f), Offset(size.width, size.height), 2f)
                drawLine(rc, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            })
        }

        AnimatedVisibility(visible = isVisible, enter = fadeIn(tween(400))) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val state = panicState) {
                    is PanicState.Ready, is PanicState.Confirming -> {
                        // Warning icon — pixel style
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .scale(if (isConfirming) pulseAlpha else 1f)
                                .drawBehind { drawRect(DestructiveRedDark, Offset(5f, 5f), size) }
                                .background(DestructiveRed.copy(alpha = 0.15f))
                                .border(2.dp, DestructiveRed.copy(if (isConfirming) pulseAlpha else 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, null, modifier = Modifier.size(52.dp), tint = DestructiveRed)
                        }

                        Spacer(Modifier.height(28.dp))

                        Text(
                            text = "EMERGENCY WIPE",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = DestructiveRed
                        )

                        Spacer(Modifier.height(10.dp))

                        // Pixel separator
                        Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                            Box(modifier = Modifier.fillMaxHeight().width(30.dp).background(DestructiveRed))
                            Box(modifier = Modifier.fillMaxSize().background(PixelBorderBright))
                        }

                        Spacer(Modifier.height(20.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth().background(DestructiveRed.copy(alpha = 0.07f)).border(1.dp, DestructiveRed.copy(alpha = 0.3f)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("All encryption keys will be destroyed", "All secured files become irrecoverable", "THIS ACTION CANNOT BE UNDONE").forEach { line ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(">", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = DestructiveRed)
                                    Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                                }
                            }
                        }

                        Spacer(Modifier.height(36.dp))

                        AnimatedContent(
                            targetState = isConfirming,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                            label = "panicContent"
                        ) { confirming ->
                            if (confirming) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Countdown — big pixel number
                                    Box(
                                        modifier = Modifier.size(80.dp)
                                            .drawBehind { drawRect(DestructiveRedDark, Offset(4f, 4f), size) }
                                            .background(DestructiveRed.copy(alpha = 0.15f))
                                            .border(2.dp, DestructiveRed),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$countdown",
                                            style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                            color = DestructiveRed
                                        )
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    Text(
                                        "WIPING IN $countdown SECOND${if (countdown != 1) "S" else ""}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                                        color = TextSecondary
                                    )

                                    Spacer(Modifier.height(24.dp))

                                    // Cancel — pixel button
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                            .background(DarkCard).border(2.dp, PixelBorderBright)
                                            .clickable { isConfirming = false; countdown = 5; onCancel() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("[ CANCEL ]", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = TextPrimary)
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Wipe button — red pixel
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                            .drawBehind { drawRect(DestructiveRedDark, Offset(3f, 3f), size) }
                                            .background(DestructiveRed)
                                            .border(1.dp, Color.Black.copy(alpha = 0.3f))
                                            .clickable { isConfirming = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.Default.DeleteForever, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            Text("WIPE EVERYTHING", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = Color.White)
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    // Cancel button — outlined pixel
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                            .background(DarkCard).border(1.dp, PixelBorderBright)
                                            .clickable(onClick = onCancel),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("CANCEL", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    is PanicState.Wiping -> {
                        Box(
                            modifier = Modifier.size(80.dp).background(DestructiveRed.copy(alpha = 0.12f)).border(2.dp, DestructiveRed),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(44.dp), color = DestructiveRed, strokeWidth = 3.dp)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("DESTROYING KEYS...", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = DestructiveRed)
                        Text("Please wait", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                    }

                    is PanicState.Complete -> {
                        val checkScale by animateFloatAsState(targetValue = 1f, animationSpec = spring(0.4f, 200f), label = "check")
                        Box(
                            modifier = Modifier.size(80.dp).scale(checkScale).background(DestructiveRed.copy(alpha = 0.12f)).border(2.dp, DestructiveRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(44.dp), tint = DestructiveRed)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("WIPE COMPLETE", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = DestructiveRed)
                        Spacer(Modifier.height(8.dp))
                        Text("${state.result.keysDestroyed} keys destroyed", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                        Text("All files are now irrecoverable", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextMuted)
                    }

                    is PanicState.Error -> {
                        Box(
                            modifier = Modifier.size(80.dp).background(DestructiveRed.copy(alpha = 0.12f)).border(2.dp, DestructiveRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Error, null, modifier = Modifier.size(44.dp), tint = DestructiveRed)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("WIPE FAILED", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = DestructiveRed)
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
