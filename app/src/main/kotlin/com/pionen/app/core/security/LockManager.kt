package com.pionen.app.core.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.concurrent.Executor
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import com.pionen.app.core.crypto.KeyManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pionen_prefs")

/**
 * LockManager: Manages vault lock/unlock state and authentication.
 * 
 * Security Design:
 * - Vault auto-locks when app goes to background
 * - Supports biometric + mandatory PIN (two-factor)
 * - PIN is hashed with PBKDF2 (100,000 iterations)
 * - Clears decryption caches on lock
 */
@Singleton
class LockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager
) {
    
    companion object {
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_LOCK_TIMEOUT = longPreferencesKey("lock_timeout_ms")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash_v2")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt_v2")
        
        private const val DEFAULT_LOCK_TIMEOUT = 5 * 60 * 1000L // 5 minutes
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val PIN_LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5-minute lockout
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
    }
    
    private val _lockState = MutableStateFlow<LockState>(LockState.Locked)
    val lockState: StateFlow<LockState> = _lockState
    
    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts
    
    // Use monotonic clock to prevent manipulation via wall-clock changes
    private var lastActivityTime = SystemClock.elapsedRealtime()
    
    // Monotonic timestamp when PIN lockout started (0 = no lockout)
    private var pinLockoutStartTime = 0L
    
    // Track if biometric step is complete (for two-factor)
    private val _biometricPassed = MutableStateFlow(false)
    val biometricPassed: StateFlow<Boolean> = _biometricPassed

    // Track if biometric prompt is currently showing (don't lock during prompt)
    private val _isBiometricPromptShowing = MutableStateFlow(false)
    val isBiometricPromptShowing: StateFlow<Boolean> = _isBiometricPromptShowing
    
    /**
     * Check if biometric authentication is available.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    /**
     * Check if PIN is configured.
     */
    val isPinConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_HASH] != null
    }
    
    /**
     * Check if PIN is configured (blocking).
     */
    suspend fun hasPinConfigured(): Boolean {
        return context.dataStore.data.first()[KEY_PIN_HASH] != null
    }

    /**
     * Set a new 6-digit PIN with PBKDF2 hashing.
     */
    suspend fun setPin(pin: String) {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN must be 6 digits" }
        
        // Generate cryptographically secure salt
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        
        // Hash with PBKDF2
        val hash = hashPinPbkdf2(pin, salt)
        
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_PIN_SALT] = saltHex
        }
    }

    /**
     * Verify PIN against stored hash.
     */
    suspend fun verifyPin(pin: String): Boolean {
        // Check PIN lockout
        if (pinLockoutStartTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - pinLockoutStartTime
            if (elapsed < PIN_LOCKOUT_DURATION_MS) {
                return false // Still locked out
            } else {
                // Lockout expired
                pinLockoutStartTime = 0
                _failedAttempts.value = 0
            }
        }
        
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[KEY_PIN_HASH] ?: return false
        val saltHex = prefs[KEY_PIN_SALT] ?: return false
        
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val inputHash = hashPinPbkdf2(pin, salt)
        
        val isValid = java.security.MessageDigest.isEqual(
            inputHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
        
        return if (isValid) {
            // Both factors complete - unlock!
            _lockState.value = LockState.Unlocked(SystemClock.elapsedRealtime(), isDecoy = false)
            _failedAttempts.value = 0
            _biometricPassed.value = false
            pinLockoutStartTime = 0
            lastActivityTime = SystemClock.elapsedRealtime()
            true
        } else {
            _failedAttempts.value++
            if (_failedAttempts.value >= MAX_FAILED_ATTEMPTS) {
                pinLockoutStartTime = SystemClock.elapsedRealtime()
            }
            false
        }
    }
    
    /**
     * Verify a PIN against stored hash WITHOUT changing lock state.
     * Used by DecoyVaultManager to detect PIN collisions.
     * 
     * @param pin The PIN to check
     * @return true if the PIN matches the stored real vault PIN
     */
    suspend fun verifyPinWithoutUnlock(pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[KEY_PIN_HASH] ?: return false
        val saltHex = prefs[KEY_PIN_SALT] ?: return false
        
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val inputHash = hashPinPbkdf2(pin, salt)
        
        return java.security.MessageDigest.isEqual(
            inputHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
    }
    
    /**
     * PBKDF2 hashing with SHA-256 + Hardware HMAC.
     */
    private fun hashPinPbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val pbkdf2Hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val protectedHash = keyManager.hashWithHardwareMac(pbkdf2Hash)
        return protectedHash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Lock the vault immediately.
     */
    fun lock() {
        _lockState.value = LockState.Locked
        _biometricPassed.value = false
    }

    /**
     * Unlock as decoy - sets Unlocked state so navigation works,
     * but the ViewModel tracks that we're in decoy mode separately.
     */
    fun unlockAsDecoy() {
        _lockState.value = LockState.Unlocked(SystemClock.elapsedRealtime(), isDecoy = true)
        _biometricPassed.value = false
        lastActivityTime = SystemClock.elapsedRealtime()
    }
    
    /**
     * Force immediate lock (e.g. when app goes to background)
     */
    fun lockVault() {
        _lockState.value = LockState.Locked
        _biometricPassed.value = false
    }
    
    /**
     * Authenticate with biometrics (step 1 of 2-factor).
     */
    suspend fun authenticateBiometric(activity: FragmentActivity): UnlockResult {
        if (!isBiometricAvailable()) {
            return UnlockResult.Error("Biometric authentication not available")
        }

        return suspendCoroutine { continuation ->
            _isBiometricPromptShowing.value = true
            val executor: Executor = ContextCompat.getMainExecutor(context)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _isBiometricPromptShowing.value = false
                    _biometricPassed.value = true
                    _failedAttempts.value = 0
                    continuation.resume(UnlockResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    _isBiometricPromptShowing.value = false
                    _failedAttempts.value++
                    if (_failedAttempts.value >= MAX_FAILED_ATTEMPTS) {
                        continuation.resume(UnlockResult.TooManyAttempts)
                    } else {
                        continuation.resume(UnlockResult.Error(errString.toString()))
                    }
                }

                override fun onAuthenticationFailed() {
                    // A single biometric attempt failed (e.g. bad fingerprint)
                    // but the prompt is still showing — do NOT resume here.
                    // The prompt will call onAuthenticationError when fully dismissed.
                    _failedAttempts.value++
                    if (_failedAttempts.value >= MAX_FAILED_ATTEMPTS) {
                        _isBiometricPromptShowing.value = false
                        continuation.resume(UnlockResult.TooManyAttempts)
                    }
                    // If below threshold, leave prompt open - system handles retry
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Pionen")
                .setSubtitle("Step 1: Verify your identity")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }
    
    /**
     * Called when app goes to background.
     * Always locks unconditionally — if a biometric prompt was showing,
     * Android will dismiss it automatically and the user must re-authenticate.
     * This prevents the bypass where backgrounding during a prompt skips locking.
     */
    fun onAppBackgrounded() {
        _isBiometricPromptShowing.value = false
        lock()
    }
    
    /**
     * Called when app returns to foreground.
     */
    fun onAppForegrounded() {
        // Vault remains locked
    }
    
    /**
     * Record user activity.
     */
    fun recordActivity() {
        lastActivityTime = SystemClock.elapsedRealtime()
    }
    
    /**
     * Check inactivity lock.
     *
     * TODO: This method is not currently called from anywhere. To enable
     *       inactivity-based auto-lock, wire it into a periodic check
     *       (e.g. via a LaunchedEffect timer in the Vault screen).
     */
    suspend fun checkInactivityLock() {
        val timeout = context.dataStore.data.first()[KEY_LOCK_TIMEOUT] ?: DEFAULT_LOCK_TIMEOUT
        if (timeout > 0 && SystemClock.elapsedRealtime() - lastActivityTime > timeout) {
            lock()
        }
    }
    
    /**
     * Set lock timeout.
     */
    suspend fun setLockTimeout(timeoutMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCK_TIMEOUT] = timeoutMs
        }
    }
    
    /**
     * Reset failed attempts.
     */
    fun resetFailedAttempts() {
        _failedAttempts.value = 0
    }
}

/**
 * Vault lock state.
 */
sealed class LockState {
    object Locked : LockState()
    data class Unlocked(val unlockedAt: Long, val isDecoy: Boolean = false) : LockState()
}

/**
 * Result of an unlock attempt.
 */
sealed class UnlockResult {
    object Success : UnlockResult()
    object TooManyAttempts : UnlockResult()
    data class Error(val message: String) : UnlockResult()
}
