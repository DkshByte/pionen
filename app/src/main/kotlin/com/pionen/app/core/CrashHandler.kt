package com.pionen.app.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.pionen.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * CrashHandler: Global uncaught exception handler for production stability.
 * 
 * Security Design:
 * - Never logs sensitive data (PINs, file contents, keys)
 * - Provides graceful degradation
 * - Can trigger panic wipe on certain critical errors (optional)
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "PionenCrash"
        private var instance: CrashHandler? = null
        
        /**
         * Install the global crash handler.
         * Call this in Application.onCreate()
         */
        fun install(context: Context) {
            if (instance == null) {
                val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                instance = CrashHandler(context.applicationContext, defaultHandler)
                Thread.setDefaultUncaughtExceptionHandler(instance)
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Log crash securely (no sensitive data)
            logCrashSecurely(thread, throwable)
            
            // Clear any sensitive data from memory
            clearSensitiveData()
            
        } catch (e: Exception) {
            // If our handler fails, don't make things worse
        } finally {
            // Let the default handler take over (shows crash dialog or restarts)
            defaultHandler?.uncaughtException(thread, throwable)
                ?: run {
                    // If no default handler, terminate gracefully
                    exitProcess(1)
                }
        }
    }

    private fun logCrashSecurely(thread: Thread, throwable: Throwable) {
        // Only log in debug builds
        if (!BuildConfig.ENABLE_LOGGING) return
        
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()
        
        // Sanitize stack trace - remove any potential sensitive data
        val sanitizedTrace = sanitizeStackTrace(stackTrace)
        
        Log.e(TAG, """
            |=== PIONEN CRASH REPORT ===
            |Thread: ${thread.name}
            |Exception: ${throwable.javaClass.simpleName}
            |Message: ${sanitizeMessage(throwable.message)}
            |Device: ${Build.MANUFACTURER} ${Build.MODEL}
            |Android: ${Build.VERSION.SDK_INT}
            |App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            |Stack Trace:
            |$sanitizedTrace
            |===========================
        """.trimMargin())
    }

    private fun sanitizeStackTrace(trace: String): String {
        // Remove any lines that might contain sensitive info
        return trace.lines()
            .filter { line ->
                // Keep only relevant Pionen and Android framework lines
                line.contains("com.pionen") || 
                line.contains("android.") ||
                line.contains("kotlin.") ||
                line.contains("java.")
            }
            .take(30) // Limit to 30 lines
            .joinToString("\n")
    }

    private fun sanitizeMessage(message: String?): String {
        if (message == null) return "null"
        
        // Remove anything that looks like sensitive data
        return message
            .replace(Regex("\\d{6}"), "[PIN]") // 6-digit numbers (PINs)
            .replace(Regex("[a-fA-F0-9]{32,}"), "[KEY]") // Hex keys
            .replace(Regex("/data/.*?/files/.*"), "[PATH]") // File paths
            .take(200) // Limit length
    }

    private fun clearSensitiveData() {
        // TODO: Could integrate with SecureBuffer to clear any decrypted content
        // For now, just run GC to help clear references
        System.gc()
    }
}
