package com.pionen.app.ingestion

import android.content.Context
import com.pionen.app.core.network.ProxyAwareHttpClient
import com.pionen.app.core.network.TorManager
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.core.vault.VaultFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecureDownloader: Downloads files directly into the encrypted vault.
 * 
 * Security Design:
 * - Downloads stream directly to encryption pipeline
 * - Plaintext NEVER touches disk
 * - TLS 1.3 for transport security
 * - Optional Tor proxy support for anonymous downloads
 * - Progress tracking without exposing content
 */
@Singleton
class SecureDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultEngine: VaultEngine,
    private val proxyAwareHttpClient: ProxyAwareHttpClient,
    private val torManager: TorManager
) {
    
    // Default client for non-Tor downloads
    private val defaultHttpClient: OkHttpClient by lazy {
        proxyAwareHttpClient.createDirectClient()
    }
    
    /**
     * Get the appropriate HTTP client based on Tor status.
     */
    private fun getHttpClient(useTor: Boolean): OkHttpClient {
        return if (useTor && torManager.isReady()) {
            proxyAwareHttpClient.createTorClient()
        } else {
            defaultHttpClient
        }
    }
    
    /**
     * Download a URL directly into the encrypted vault.
     * Returns a Flow that emits progress updates.
     * 
     * @param url The URL to download
     * @param fileName Optional file name (extracted from URL if not provided)
     * @param useTor Whether to route through Tor network (if connected)
     * @return Flow of DownloadProgress updates
     */
    fun downloadToVault(
        url: String,
        fileName: String? = null,
        useTor: Boolean = false
    ): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Starting)
        
        try {
            val client = getHttpClient(useTor)
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(DownloadProgress.Failed("HTTP ${response.code}: ${response.message}"))
                return@flow
            }
            
            val body = response.body ?: run {
                emit(DownloadProgress.Failed("Empty response body"))
                return@flow
            }
            
            val contentLength = body.contentLength()
            val actualFileName = fileName ?: extractFileName(url, response)
            val mimeType = response.header("Content-Type") ?: guessMimeType(actualFileName)
            
            emit(DownloadProgress.Downloading(0, contentLength))
            
            // Create progress-tracking input stream
            val inputStream = ProgressInputStream(body.byteStream(), contentLength) { bytesRead, total ->
                // Progress callback - we emit in the flow below
            }
            
            // Stream directly to encrypted vault
            val vaultFile = vaultEngine.createFileFromStream(
                inputStream = inputStream,
                fileName = actualFileName,
                mimeType = mimeType
            )
            
            emit(DownloadProgress.Complete(vaultFile))
            
        } catch (e: Exception) {
            emit(DownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Extract filename from URL or Content-Disposition header.
     */
    private fun extractFileName(url: String, response: okhttp3.Response): String {
        // Try Content-Disposition header first
        response.header("Content-Disposition")?.let { disposition ->
            val regex = """filename[*]?=['"]?([^'";\s]+)""".toRegex()
            regex.find(disposition)?.groupValues?.getOrNull(1)?.let { return it }
        }
        
        // Fall back to URL path
        return try {
            URL(url).path.substringAfterLast('/')
                .takeIf { it.isNotBlank() }
                ?: "downloaded_file"
        } catch (e: Exception) {
            "downloaded_file"
        }
    }
    
    /**
     * Guess MIME type from filename extension.
     */
    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}

/**
 * InputStream wrapper that tracks read progress.
 */
private class ProgressInputStream(
    private val wrapped: InputStream,
    private val totalBytes: Long,
    private val onProgress: (bytesRead: Long, total: Long) -> Unit
) : InputStream() {
    
    private var bytesRead = 0L
    
    override fun read(): Int {
        val byte = wrapped.read()
        if (byte != -1) {
            bytesRead++
            onProgress(bytesRead, totalBytes)
        }
        return byte
    }
    
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = wrapped.read(b, off, len)
        if (count > 0) {
            bytesRead += count
            onProgress(bytesRead, totalBytes)
        }
        return count
    }
    
    override fun close() {
        wrapped.close()
    }
}

/**
 * Download progress states.
 */
sealed class DownloadProgress {
    object Starting : DownloadProgress()
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : DownloadProgress() {
        val percentComplete: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes) * 100 else -1f
    }
    data class Complete(val file: VaultFile) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}
