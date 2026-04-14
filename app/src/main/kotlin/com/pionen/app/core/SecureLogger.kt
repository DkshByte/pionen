package com.pionen.app.core

import android.util.Log
import com.pionen.app.BuildConfig

/**
 * SecureLogger: Production-safe logging wrapper.
 * 
 * Features:
 * - Completely disabled in release builds
 * - Auto-sanitizes sensitive data patterns
 * - No disk writes (memory only)
 */
object SecureLogger {
    
    private const val TAG = "Pionen"
    
    /**
     * Debug level logging - development only.
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.d("$TAG/$tag", sanitize(message))
        }
    }
    
    /**
     * Info level logging - development only.
     */
    fun i(tag: String, message: String) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.i("$TAG/$tag", sanitize(message))
        }
    }
    
    /**
     * Warning level logging - development only.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.ENABLE_LOGGING) {
            if (throwable != null) {
                Log.w("$TAG/$tag", sanitize(message), throwable)
            } else {
                Log.w("$TAG/$tag", sanitize(message))
            }
        }
    }
    
    /**
     * Error level logging - development only.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.ENABLE_LOGGING) {
            if (throwable != null) {
                Log.e("$TAG/$tag", sanitize(message), throwable)
            } else {
                Log.e("$TAG/$tag", sanitize(message))
            }
        }
    }
    
    /**
     * Security event logging - debug/dev only.
     *
     * Even highly-sanitised metadata (e.g. "auth failed") can be useful for
     * an attacker with logcat access. Gated behind ENABLE_LOGGING for release.
     */
    fun security(tag: String, event: String) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.i("$TAG/Security/$tag", sanitizeSecurityEvent(event))
        }
    }
    
    /**
     * Sanitize message to remove sensitive data.
     */
    private fun sanitize(message: String): String {
        return message
            .replace(Regex("\\d{6}"), "[***]")           // 6-digit PINs
            .replace(Regex("\\d{4,}"), "[NUM]")          // Other long numbers
            .replace(Regex("[a-fA-F0-9]{16,}"), "[KEY]") // Hex keys/tokens
            .replace(Regex("password[=:]\\S+", RegexOption.IGNORE_CASE), "password=[***]")
            .replace(Regex("pin[=:]\\S+", RegexOption.IGNORE_CASE), "pin=[***]")
            .replace(Regex("/data/.*?/files/[^\\s]+"), "[VAULT_PATH]")
    }
    
    /**
     * Extra sanitization for security events.
     */
    private fun sanitizeSecurityEvent(event: String): String {
        return event
            .replace(Regex("\\d+"), "X") // Remove all numbers
            .take(100) // Limit length
    }
}
