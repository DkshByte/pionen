package com.pionen.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.vault.DecryptedContent
import com.pionen.app.core.vault.FileType
import com.pionen.app.core.vault.VaultFile
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.GalleryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gallery-style viewer with horizontal swipe navigation.
 * Minimal Apple-inspired design with true black background.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    startIndex: Int,
    onBack: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState(initial = emptyList())
    val currentContent by viewModel.currentContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Only show viewable files in gallery
    val viewableFiles = remember(files) {
        files.filter { file: VaultFile ->
            val type = FileType.fromMimeType(file.mimeType)
            type == FileType.IMAGE || type == FileType.VIDEO || type == FileType.TEXT
        }
    }
    
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (viewableFiles.size - 1).coerceAtLeast(0)),
        pageCount = { viewableFiles.size }
    )
    
    // Load content when page changes
    LaunchedEffect(pagerState.currentPage, viewableFiles) {
        if (viewableFiles.isNotEmpty() && pagerState.currentPage < viewableFiles.size) {
            viewModel.loadFile(viewableFiles[pagerState.currentPage].id)
        }
    }
    
    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose { viewModel.cleanup() }
    }
    
    // Header visibility (auto-hide)
    var showControls by remember { mutableStateOf(true) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
    ) {
        if (viewableFiles.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📁", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No viewable files",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { viewableFiles[it].id }
            ) { page ->
                val file = viewableFiles[page]
                
                GalleryPage(
                    file = file,
                    content = if (page == pagerState.currentPage) currentContent else null,
                    isLoading = page == pagerState.currentPage && isLoading,
                    mediaPlayer = viewModel.mediaPlayer
                )
            }
        }
        
        // Top bar — pixel style (animated)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close — pixel square button
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, PixelBorderBright)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // File info — pixel chip
                    if (viewableFiles.isNotEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        ) {
                            Text(
                                viewableFiles.getOrNull(pagerState.currentPage)?.fileName ?: "",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .border(1.dp, NeonGreen.copy(alpha = 0.3f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "${pagerState.currentPage + 1} / ${viewableFiles.size}",
                                    color = NeonGreen,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }

                    // Share — pixel square button
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(1.dp, PixelBorderBright)
                            .clickable { viewableFiles.getOrNull(pagerState.currentPage)?.let {} },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        // Bottom pixel indicator dots
        if (viewableFiles.size in 2..10 && showControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(viewableFiles.size) { index ->
                    // Pixel squares instead of circles
                    Box(
                        modifier = Modifier
                            .size(width = if (index == pagerState.currentPage) 18.dp else 6.dp, height = 4.dp)
                            .background(
                                if (index == pagerState.currentPage)
                                    NeonGreen
                                else
                                    Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
        
        // Counter for > 10 items
        if (viewableFiles.size > 10 && showControls) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${viewableFiles.size}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun GalleryPage(
    file: VaultFile,
    content: DecryptedContent?,
    isLoading: Boolean,
    mediaPlayer: com.pionen.app.media.SecureMediaPlayer
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading || content == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Decrypting...",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            else -> {
                val fileType = FileType.fromMimeType(file.mimeType)
                when (fileType) {
                    FileType.IMAGE -> GalleryImageViewer(content)
                    FileType.VIDEO -> GalleryVideoPlayer(content, mediaPlayer)
                    FileType.TEXT -> GalleryTextViewer(content)
                    else -> {
                        Text(
                            "Cannot preview this file type",
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryImageViewer(content: DecryptedContent) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    val bitmap = remember(content) {
        try {
            val data = content.buffer.getData()
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = content.file.fileName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                },
            contentScale = ContentScale.Fit
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Could not decode image",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun GalleryVideoPlayer(
    content: DecryptedContent,
    mediaPlayer: com.pionen.app.media.SecureMediaPlayer
) {
    val isPlaying by mediaPlayer.isPlaying.collectAsState()
    val currentPosition by mediaPlayer.currentPosition.collectAsState()
    val duration by mediaPlayer.duration.collectAsState()
    val playbackState by mediaPlayer.playbackState.collectAsState()
    
    // UI State
    var showControls by remember { mutableStateOf(true) }
    var controlsAutoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    
    // Zoom state for pinch-to-zoom
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Animated scale for smooth transitions
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // Auto-hide controls after 3 seconds when playing
    fun resetAutoHide() {
        controlsAutoHideJob?.cancel()
        if (isPlaying) {
            controlsAutoHideJob = scope.launch {
                kotlinx.coroutines.delay(3000)
                showControls = false
            }
        }
    }
    
    LaunchedEffect(content) {
        try {
            mediaPlayer.loadMedia(content.buffer, content.file.mimeType)
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    // Update position periodically while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer.updatePosition()
            kotlinx.coroutines.delay(250) // More frequent updates for smoother progress
        }
    }
    
    // Auto-hide controls when video starts playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            resetAutoHide()
        } else {
            controlsAutoHideJob?.cancel()
            showControls = true
        }
    }
    
    DisposableEffect(content) {
        onDispose {
            controlsAutoHideJob?.cancel()
            mediaPlayer.release()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Pinch to zoom
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                        // Limit panning based on scale
                        val maxOffset = (scale - 1f) * size.width / 2
                        offsetX = offsetX.coerceIn(-maxOffset, maxOffset)
                        offsetY = offsetY.coerceIn(-maxOffset, maxOffset)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        if (showControls) resetAutoHide()
                    },
                    onDoubleTap = { offset ->
                        // Double tap to zoom or reset
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2f
                            // Zoom towards tap position
                            offsetX = (size.width / 2 - offset.x) * 0.5f
                            offsetY = (size.height / 2 - offset.y) * 0.5f
                        }
                    }
                )
            }
    ) {
        // Video surface with zoom support
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentAlignment = Alignment.Center
        ) {
            mediaPlayer.getPlayer()?.let { exoPlayer ->
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Loading indicator
        androidx.compose.animation.AnimatedVisibility(
            visible = playbackState is com.pionen.app.media.PlaybackState.Buffering,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Controls overlay with smooth animations
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient for status bar visibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Center controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip back 10s
                    IconButton(
                        onClick = {
                            mediaPlayer.seekTo(maxOf(0, currentPosition - 10000))
                            resetAutoHide()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Skip back 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Play/Pause button
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.pause()
                            } else {
                                mediaPlayer.play()
                            }
                            resetAutoHide()
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    
                    // Skip forward 10s
                    IconButton(
                        onClick = {
                            mediaPlayer.seekTo(minOf(duration, currentPosition + 10000))
                            resetAutoHide()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "Skip forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                // Bottom controls with progress
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Time and progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current time
                        Text(
                            text = formatDuration(currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.width(48.dp)
                        )
                        
                        // Progress slider
                        val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                        var sliderPosition by remember { mutableFloatStateOf(progress) }
                        var isDragging by remember { mutableStateOf(false) }
                        
                        // Update slider position when not dragging
                        LaunchedEffect(progress, isDragging) {
                            if (!isDragging) {
                                sliderPosition = progress
                            }
                        }
                        
                        Slider(
                            value = sliderPosition,
                            onValueChange = { newValue ->
                                isDragging = true
                                sliderPosition = newValue
                                resetAutoHide()
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                mediaPlayer.seekTo((sliderPosition * duration).toLong())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        
                        // Duration
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
                
                // Zoom indicator
                if (scale > 1.05f) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun GalleryTextViewer(content: DecryptedContent) {
    val text = remember(content) {
        try {
            String(content.buffer.getData())
        } catch (e: Exception) {
            "Could not decode text"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141414)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 22.sp
                ),
                color = Color.White.copy(alpha = 0.9f)
            )
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return format.format(Date(timestamp))
}
