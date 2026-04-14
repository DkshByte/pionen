package com.pionen.app.core.network

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
import com.pionen.app.R
import com.pionen.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * TorService: Foreground service for maintaining Tor connection.
 * 
 * Security Design:
 * - Keeps Tor connection alive in background
 * - Shows persistent notification with connection status
 * - Properly handles lifecycle and cleanup
 */
@AndroidEntryPoint
class TorService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "tor_service_channel"
        const val ACTION_START = "com.pionen.app.TOR_START"
        const val ACTION_STOP = "com.pionen.app.TOR_STOP"
        
        fun startService(context: Context) {
            val intent = Intent(context, TorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, TorService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
    
    @Inject
    lateinit var torManager: TorManager
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification(TorConnectionState.Connecting))
                startTorAndMonitor()
            }
            ACTION_STOP -> {
                torManager.stopTor()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    private fun startTorAndMonitor() {
        torManager.startTor()
        
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            torManager.connectionState.collectLatest { state ->
                updateNotification(state)
                
                // Stop service on disconnect or error
                if (state == TorConnectionState.Disconnected) {
                    delay(500) // Brief delay to show disconnected state
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }
    
    private fun updateNotification(state: TorConnectionState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(state))
    }
    
    private fun createNotification(state: TorConnectionState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val (title, text, iconRes) = when (state) {
            is TorConnectionState.Connecting -> Triple(
                "Connecting to Tor",
                "Establishing secure connection...",
                android.R.drawable.ic_popup_sync
            )
            is TorConnectionState.Connected -> Triple(
                "Tor Connected",
                "Browsing anonymously via Tor",
                android.R.drawable.ic_lock_lock
            )
            is TorConnectionState.Error -> Triple(
                "Tor Error",
                state.message,
                android.R.drawable.ic_dialog_alert
            )
            is TorConnectionState.Disconnected -> Triple(
                "Tor Disconnected",
                "Connection closed",
                android.R.drawable.ic_lock_idle_lock
            )
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .setOngoing(state is TorConnectionState.Connected || state is TorConnectionState.Connecting)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tor Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Tor connection status"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
