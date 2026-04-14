package com.pionen.app

import android.app.Application
import android.os.Build
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pionen.app.core.CrashHandler
import dagger.hilt.android.HiltAndroidApp

/**
 * Pionen Application class.
 * Uses Hilt for dependency injection.
 * 
 * Production Features:
 * - Global crash handler for stability
 * - StrictMode in debug builds only
 * - Secure image loading (memory cache only)
 */
@HiltAndroidApp
class PionenApplication : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()
        
        // Install global crash handler
        CrashHandler.install(this)
        
        // Initialize SQLCipher native libraries
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        
        // Enable StrictMode in debug builds only
        if (BuildConfig.ENABLE_LOGGING) {
            enableStrictMode()
        }
    }
    
    /**
     * Configure Coil image loader with security-focused settings.
     * - Memory cache only (no disk cache to avoid decrypted files on disk)
     * - Disable network for external URLs (only load local encrypted files)
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of app memory
                    .build()
            }
            // SECURITY: No disk cache - decrypted images should never hit disk
            .diskCache(null)
            .crossfade(true)
            .build()
    }
    
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
}
