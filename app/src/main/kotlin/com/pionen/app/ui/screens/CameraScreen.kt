package com.pionen.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.CameraViewModel
import java.util.UUID

/**
 * Pixel-art Secure Camera Screen.
 * Full-bleed viewfinder · flat pixel controls · neon green capture button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onFileCaptured: (UUID) -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) { if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA) }

    val isCapturing by viewModel.isCapturing.collectAsState()
    val error by viewModel.error.collectAsState()
    var useFrontCamera by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); controlsVisible = true }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            // Camera Preview
            var previewView by remember { mutableStateOf<PreviewView?>(null) }

            AndroidView(
                factory = { ctx -> PreviewView(ctx).also { previewView = it } },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(previewView, useFrontCamera) {
                previewView?.let { pv ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val imageCapture = viewModel.getImageCapture()
                        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(context))
                }
            }

            // ─── Top HUD bar ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back — pixel square
                    Box(
                        modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, PixelBorderBright).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Title chip
                    Row(
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).border(1.dp, NeonGreen.copy(alpha = 0.4f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(NeonGreen))
                        Text("SECURE CAM", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = NeonGreen)
                    }

                    // Flip camera — pixel square
                    Box(
                        modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, PixelBorderBright).clickable { useFrontCamera = !useFrontCamera },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Cameraswitch, "Flip", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ─── Bottom Controls ───
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400, easing = PionenEasing.EaseOut)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error
                    AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                        Text("> ${error ?: ""}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = DestructiveRed, modifier = Modifier.padding(bottom = 12.dp))
                    }

                    // Capture button
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .drawBehind {
                                if (!isPressed) {
                                    // Pixel outer ring shadow
                                    drawRect(NeonGreenDark, Offset(4f, 4f), size)
                                }
                            }
                            .background(if (isCapturing) NeonGreen.copy(alpha = 0.5f) else NeonGreen)
                            .border(2.dp, Color.White.copy(alpha = 0.3f))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = !isCapturing
                            ) {
                                viewModel.capturePhoto { onFileCaptured(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CameraAlt, "Capture", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Security badge
                    Row(
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).border(1.dp, NeonGreen.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = NeonGreen, modifier = Modifier.size(10.dp))
                        Text("ENCRYPTED → VAULT", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp), color = NeonGreen.copy(alpha = 0.8f))
                    }
                }
            }
        } else {
            // Permission state
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(80.dp).background(DarkCard).border(2.dp, NeonGreen.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(40.dp), tint = TextMuted)
                }
                Spacer(Modifier.height(20.dp))
                Text("CAMERA PERMISSION REQUIRED", style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.drawBehind { drawRect(NeonGreenDark, Offset(3f, 3f), size) }.background(NeonGreen).clickable { launcher.launch(Manifest.permission.CAMERA) }.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("GRANT PERMISSION", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = Color.Black)
                }
            }
        }
    }
}
