package com.pionen.app.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pionen.app.core.SecureLogger
import com.pionen.app.core.security.LockManager
import com.pionen.app.core.security.LockState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * Foreground Service for running the secure web server.
 * 
 * Keeps the server running even when app is in background.
 * Shows persistent notification with stop action.
 */
@AndroidEntryPoint
class WebServerService : Service() {
    
    companion object {
        private const val TAG = "WebServerService"
        private const val CHANNEL_ID = "pionen_web_server"
        private const val NOTIFICATION_ID = 2001
        
        const val ACTION_START = "com.pionen.app.server.START"
        const val ACTION_STOP = "com.pionen.app.server.STOP"
        
        fun startServer(context: Context) {
            val intent = Intent(context, WebServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopServer(context: Context) {
            val intent = Intent(context, WebServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
    
    @Inject
    lateinit var secureWebServer: SecureWebServer
    
    @Inject
    lateinit var lockManager: LockManager
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServerInternal()
            ACTION_STOP -> stopServerInternal()
        }
        return START_STICKY
    }
    
    private fun startServerInternal() {
        SecureLogger.i(TAG, "Starting web server service")
        
        // SECURITY: Wire lock-state check so API requests are gated
        secureWebServer.isVaultUnlocked = { lockManager.lockState.value !is LockState.Locked }
        
        val result = secureWebServer.start()
        
        result.fold(
            onSuccess = { info ->
                startForeground(NOTIFICATION_ID, createNotification(info))
                
                // Monitor server state
                scope.launch {
                    secureWebServer.serverState.collectLatest { state ->
                        when (state) {
                            is SecureWebServer.ServerState.Stopped -> {
                                stopSelf()
                            }
                            is SecureWebServer.ServerState.Running -> {
                                secureWebServer.serverInfo.value?.let { info ->
                                    updateNotification(info)
                                }
                            }
                            is SecureWebServer.ServerState.Error -> {
                                SecureLogger.e(TAG, "Server error: ${state.message}")
                                stopSelf()
                            }
                            else -> {}
                        }
                    }
                }
            },
            onFailure = { error ->
                SecureLogger.e(TAG, "Failed to start server", error)
                stopSelf()
            }
        )
    }
    
    private fun stopServerInternal() {
        SecureLogger.i(TAG, "Stopping web server service")
        secureWebServer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Web Access Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Secure web server for file access"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(info: SecureWebServer.ServerInfo): Notification {
        // Stop action intent
        val stopIntent = Intent(this, WebServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🌐 Web Access Active")
            .setContentText("${info.ipAddress}:${info.port}")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Server",
                stopPendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Files accessible at:\n${info.url}\n\nUse the access token shown in-app to connect."))
            .build()
    }
    
    private fun updateNotification(info: SecureWebServer.ServerInfo) {
        val notification = createNotification(info)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        secureWebServer.stop()
        SecureLogger.i(TAG, "Web server service destroyed")
    }
}
