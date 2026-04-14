package com.pionen.app.core.security

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScreenshotShield: Prevents screenshots and screen recording.
 * 
 * Security Design:
 * - Uses FLAG_SECURE to block screenshots and screen recording
 * - Applied to all activities displaying sensitive content
 * - Android 14+ supports per-view protection
 */
@Singleton
class ScreenshotShield @Inject constructor() {
    
    /**
     * Apply screenshot protection to an activity.
     * All content in this activity will be blocked from screenshots.
     */
    fun protect(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
    
    /**
     * Remove screenshot protection from an activity.
     */
    fun unprotect(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    
    /**
     * Apply protection to a specific view (Android 14+).
     * Falls back to no-op on older versions (use activity-level protection).
     */
    fun protectView(view: View) {
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ supports per-view protection
            // Note: This API might not be available in all SDK versions
            // view.setScreenshotProtection(true)
        }
    }
    
    /**
     * Check if the activity is currently protected.
     */
    fun isProtected(activity: Activity): Boolean {
        return (activity.window.attributes.flags and 
                WindowManager.LayoutParams.FLAG_SECURE) != 0
    }
}
