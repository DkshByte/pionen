package com.pionen.app.ui.viewmodels

import android.content.Context
import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.network.ProxyAwareHttpClient
import com.pionen.app.core.network.TorConnectionState
import com.pionen.app.core.network.TorManager
import com.pionen.app.core.vault.VaultEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for SecureBrowserScreen handling download interception.
 */
@HiltViewModel
class SecureBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultEngine: VaultEngine,
    private val torManager: TorManager,
    private val proxyAwareHttpClient: ProxyAwareHttpClient
) : ViewModel() {
    
    private val _downloadProgress = MutableStateFlow<DownloadState?>(null)
    val downloadProgress: StateFlow<DownloadState?> = _downloadProgress
    
    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError
    
    // Tor state for browser UI
    val torConnectionState: StateFlow<TorConnectionState> = torManager.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TorConnectionState.Disconnected
        )
    
    val isTorEnabled: StateFlow<Boolean> = torManager.isEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    /**
     * Get the appropriate HTTP client based on current Tor status.
     */
    private fun getHttpClient(): OkHttpClient {
        return if (torManager.isReady()) {
            proxyAwareHttpClient.createTorClient()
        } else {
            proxyAwareHttpClient.createDirectClient()
        }
    }
    
    /**
     * Download a file directly to the encrypted vault.
     */
    fun downloadToVault(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        viewModelScope.launch {
            try {
                // Extract filename
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                _downloadProgress.value = DownloadState(fileName = fileName, percentage = -1, isComplete = false)
                
                withContext(Dispatchers.IO) {
                    val requestBuilder = Request.Builder().url(url)
                    
                    // Add user agent if provided
                    userAgent?.let { requestBuilder.header("User-Agent", it) }
                    
                    val response = getHttpClient().newCall(requestBuilder.build()).execute()
                    
                    if (!response.isSuccessful) {
                        _downloadError.value = "HTTP ${response.code}"
                        _downloadProgress.value = null
                        return@withContext
                    }
                    
                    val body = response.body
                    if (body == null) {
                        _downloadError.value = "Empty response"
                        _downloadProgress.value = null
                        return@withContext
                    }
                    
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    
                    // Read the entire file
                    val bytes = inputStream.readBytes()
                    
                    // Determine mime type
                    val finalMimeType = mimeType 
                        ?: response.header("Content-Type") 
                        ?: guessMimeType(fileName)
                    
                    // Encrypt and store in vault
                    vaultEngine.createFile(
                        content = bytes,
                        fileName = fileName,
                        mimeType = finalMimeType
                    )
                    
                    _downloadProgress.value = DownloadState(
                        fileName = fileName,
                        percentage = 100,
                        isComplete = true
                    )
                }
                
            } catch (e: Exception) {
                _downloadError.value = e.message ?: "Download failed"
                _downloadProgress.value = null
            }
        }
    }
    
    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
    
    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }
    
    fun clearDownloadError() {
        _downloadError.value = null
    }
}

/**
 * Download state for UI.
 */
data class DownloadState(
    val fileName: String,
    val percentage: Int, // -1 for indeterminate
    val isComplete: Boolean
)
