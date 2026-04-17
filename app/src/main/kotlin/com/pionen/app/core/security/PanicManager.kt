package com.pionen.app.core.security

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pionen.app.core.vault.PanicWipeResult
import com.pionen.app.core.vault.VaultEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PanicManager: Manages emergency wipe functionality.
 * 
 * Security Design:
 * - Provides instant crypto-shredding of all vault content
 * - Supports multiple panic triggers (gesture, PIN, etc.)
 * - Confirmation countdown to prevent accidental wipes
 * - All keys destroyed = all content irrecoverable
 */
@Singleton
class PanicManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultEngine: VaultEngine,
    private val lockManager: LockManager
) {
    
    companion object {
        private const val CONFIRMATION_COUNTDOWN_MS = 5000L // 5 seconds
        private const val PANIC_VIBRATION_PATTERN = 100L
    }
    
    private val _panicState = MutableStateFlow<PanicState>(PanicState.Ready)
    val panicState: StateFlow<PanicState> = _panicState
    
    /**
     * Initiate panic wipe with confirmation countdown.
     * User can cancel during countdown.
     */
    suspend fun initiatePanicWipe(): PanicState {
        _panicState.value = PanicState.Confirming(CONFIRMATION_COUNTDOWN_MS)
        return _panicState.value
    }
    
    /**
     * Cancel a pending panic wipe.
     */
    fun cancelPanicWipe() {
        if (_panicState.value is PanicState.Confirming) {
            _panicState.value = PanicState.Ready
        }
    }
    
    /**
     * Execute the panic wipe after confirmation.
     * IRREVERSIBLE: All encryption keys will be destroyed.
     */
    suspend fun executePanicWipe(): PanicWipeResult {
        _panicState.value = PanicState.Wiping
        
        // Vibrate to indicate wipe in progress
        vibrateWarning()
        
        try {
            // Execute the wipe
            val result = vaultEngine.panicWipe()
            
            // Lock the vault
            lockManager.lock()
            
            _panicState.value = PanicState.Complete(result)
            return result
        } catch (e: Exception) {
            _panicState.value = PanicState.Error(e.message ?: "Wipe failed")
            throw e
        }
    }
    
    
    /**
     * Detect tampering (root, debug, etc.)
     * Note: Determined attackers can bypass these checks.
     */
    fun detectTampering(): Boolean {
        return isDeviceRooted() || isDebuggerAttached()
    }
    
    /**
     * Basic root detection.
     * Note: Not foolproof - sophisticated users can bypass.
     */
    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { java.io.File(it).exists() }
    }
    
    /**
     * Check if debugger is attached.
     */
    private fun isDebuggerAttached(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }
    
    /**
     * Vibrate to warn user of panic wipe.
     */
    private fun vibrateWarning() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        PANIC_VIBRATION_PATTERN,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(PANIC_VIBRATION_PATTERN)
            }
        }
    }
}

/**
 * State of panic wipe operation.
 */
sealed class PanicState {
    object Ready : PanicState()
    data class Confirming(val countdownMs: Long) : PanicState()
    object Wiping : PanicState()
    data class Complete(val result: PanicWipeResult) : PanicState()
    data class Error(val message: String) : PanicState()
}
