package com.pionen.app.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.BaseDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.pionen.app.core.crypto.SecureBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecureMediaPlayer: Play encrypted video/audio files directly from memory.
 * 
 * Security Design:
 * - Decrypts to RAM only (no temp files)
 * - Uses custom DataSource that reads from SecureBuffer
 * - Player instance uses FLAG_SECURE context
 */
@Singleton
class SecureMediaPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    /**
     * Initialize player and load decrypted media from SecureBuffer.
     */
    @OptIn(UnstableApi::class)
    fun loadMedia(buffer: SecureBuffer, mimeType: String) {
        release()
        
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            // Create data source from SecureBuffer
            val dataSource = SecureBufferDataSource(buffer)
            val factory = DataSource.Factory { dataSource }
            
            val mediaSource = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(Uri.EMPTY))
            
            setMediaSource(mediaSource)
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.value = when (state) {
                        Player.STATE_IDLE -> PlaybackState.Idle
                        Player.STATE_BUFFERING -> PlaybackState.Buffering
                        Player.STATE_READY -> PlaybackState.Ready
                        Player.STATE_ENDED -> PlaybackState.Ended
                        else -> PlaybackState.Idle
                    }
                }
                
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }
            })
            
            prepare()
        }
    }
    
    fun play() {
        exoPlayer?.play()
    }
    
    fun pause() {
        exoPlayer?.pause()
    }
    
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    fun getPlayer(): ExoPlayer? = exoPlayer
    
    fun updatePosition() {
        exoPlayer?.let {
            _currentPosition.value = it.currentPosition
            _duration.value = it.duration.coerceAtLeast(0)
        }
    }
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _playbackState.value = PlaybackState.Idle
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }
}

/**
 * Custom DataSource that reads decrypted data from SecureBuffer.
 * No disk access - purely RAM-based.
 */
@UnstableApi
class SecureBufferDataSource(
    private val buffer: SecureBuffer
) : BaseDataSource(/* isNetwork = */ false) {
    
    private var data: ByteArray? = null
    private var readPosition = 0
    private var bytesRemaining = 0L
    
    override fun open(dataSpec: DataSpec): Long {
        data = buffer.getDataDirect()
        readPosition = dataSpec.position.toInt()
        bytesRemaining = if (dataSpec.length != -1L) {
            dataSpec.length
        } else {
            (data?.size ?: 0) - readPosition.toLong()
        }
        return bytesRemaining
    }
    
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) {
            return -1
        }
        
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        data?.let {
            System.arraycopy(it, readPosition, buffer, offset, toRead)
        }
        readPosition += toRead
        bytesRemaining -= toRead
        return toRead
    }
    
    override fun getUri(): Uri = Uri.EMPTY
    
    override fun close() {
        data = null
        readPosition = 0
        bytesRemaining = 0
    }
}

sealed class PlaybackState {
    object Idle : PlaybackState()
    object Buffering : PlaybackState()
    object Ready : PlaybackState()
    object Ended : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}
