package com.pionen.app.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.pionen.app.core.crypto.SecureBuffer
import com.pionen.app.core.vault.DecryptedContent
import com.pionen.app.media.PlaybackState
import com.pionen.app.media.SecureMediaPlayer
import com.pionen.app.ui.theme.VaultGreen
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Video player composable for encrypted video playback.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    content: DecryptedContent,
    player: SecureMediaPlayer
) {
    val context = LocalContext.current
    val playbackState by player.playbackState.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val currentPosition by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    
    // Load media on first composition
    LaunchedEffect(content) {
        player.loadMedia(content.buffer, content.file.mimeType)
    }
    
    // Update position periodically
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player.updatePosition()
            delay(500)
        }
    }
    
    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Video surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            when (playbackState) {
                is PlaybackState.Buffering -> {
                    CircularProgressIndicator(color = VaultGreen)
                }
                is PlaybackState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (playbackState as PlaybackState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    // ExoPlayer view
                    player.getPlayer()?.let { exoPlayer ->
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = exoPlayer
                                    useController = false // We use custom controls
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        
        // Custom controls
        MediaControls(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPause = {
                if (isPlaying) player.pause() else player.play()
            },
            onSeek = { position -> player.seekTo(position) }
        )
    }
}

/**
 * Audio player composable for encrypted audio playback.
 */
@OptIn(UnstableApi::class)
@Composable
fun AudioPlayerView(
    content: DecryptedContent,
    player: SecureMediaPlayer
) {
    val playbackState by player.playbackState.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val currentPosition by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    
    // Load media on first composition
    LaunchedEffect(content) {
        player.loadMedia(content.buffer, content.file.mimeType)
    }
    
    // Update position periodically
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player.updatePosition()
            delay(500)
        }
    }
    
    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large audio icon
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = VaultGreen
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // File name
        Text(
            text = content.file.fileName,
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = content.file.formattedSize,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Loading indicator
        if (playbackState is PlaybackState.Buffering) {
            CircularProgressIndicator(
                color = VaultGreen,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Controls
        MediaControls(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPause = {
                if (isPlaying) player.pause() else player.play()
            },
            onSeek = { position -> player.seekTo(position) }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Security indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = VaultGreen
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Streaming from encrypted vault",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Shared media control bar.
 */
@Composable
private fun MediaControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Progress bar
        val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
        
        Slider(
            value = progress,
            onValueChange = { newValue ->
                onSeek((newValue * duration).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = VaultGreen,
                activeTrackColor = VaultGreen
            )
        )
        
        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Play/Pause controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rewind 10s
            IconButton(
                onClick = { onSeek(maxOf(0, currentPosition - 10000)) }
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10 seconds"
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Play/Pause button
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = VaultGreen
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Forward 10s
            IconButton(
                onClick = { onSeek(minOf(duration, currentPosition + 10000)) }
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds"
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
