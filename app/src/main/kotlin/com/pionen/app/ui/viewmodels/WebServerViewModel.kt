package com.pionen.app.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.server.QrCodeGenerator
import com.pionen.app.server.SecureWebServer
import com.pionen.app.server.WebServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing the secure web server UI state.
 */
@HiltViewModel
class WebServerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureWebServer: SecureWebServer
) : ViewModel() {
    
    val serverState: StateFlow<SecureWebServer.ServerState> = secureWebServer.serverState
    val serverInfo: StateFlow<SecureWebServer.ServerInfo?> = secureWebServer.serverInfo
    
    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()
    
    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()
    
    init {
        // Generate QR code when server info changes
        viewModelScope.launch {
            serverInfo.collect { info ->
                if (info != null) {
                    _qrCodeBitmap.value = QrCodeGenerator.generateServerQr(info)
                } else {
                    _qrCodeBitmap.value = null
                }
            }
        }
    }
    
    /**
     * Toggle the web access dialog
     */
    fun toggleDialog() {
        _showDialog.value = !_showDialog.value
    }
    
    /**
     * Show the web access dialog
     */
    fun showDialog() {
        _showDialog.value = true
    }
    
    /**
     * Hide the dialog
     */
    fun hideDialog() {
        _showDialog.value = false
    }
    
    /**
     * Start the web server
     */
    fun startServer() {
        WebServerService.startServer(context)
    }
    
    /**
     * Stop the web server
     */
    fun stopServer() {
        WebServerService.stopServer(context)
    }
    
    /**
     * Check if server is currently running
     */
    val isRunning: Boolean
        get() = serverState.value is SecureWebServer.ServerState.Running
    
    /**
     * Get server URL for sharing
     */
    fun getShareableUrl(): String? {
        return serverInfo.value?.let { info ->
            "${info.url}?token=${info.token}"
        }
    }
    
    /**
     * Get server URL without token
     */
    fun getServerUrl(): String? = serverInfo.value?.url
    
    /**
     * Get access token
     */
    fun getAccessToken(): String? = serverInfo.value?.token
}
