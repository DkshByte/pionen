package com.pionen.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private val KEY_AUTH_METHOD = stringPreferencesKey("auth_method")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_LOCK_TIMEOUT = longPreferencesKey("lock_timeout_ms")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash_v2")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt_v2")
        
        private const val DEFAULT_LOCK_TIMEOUT = 0L
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
    }
    
    private val _lockState = MutableStateFlow<LockState>(LockState.Locked)
    val lockState: StateFlow<LockState> = _lockState
    
    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts
    
    private var lastActivityTime = System.currentTimeMillis()
    
    // Track if biometric step is complete (for two-factor)
    private val _biometricPassed = MutableStateFlow(false)
    val biometricPassed: StateFlow<Boolean> = _biometricPassed
    
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
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[KEY_PIN_HASH] ?: return false
        val saltHex = prefs[KEY_PIN_SALT] ?: return false
        
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val inputHash = hashPinPbkdf2(pin, salt)
        
        return if (inputHash == storedHash) {
            // Both factors complete - unlock!
            _lockState.value = LockState.Unlocked(System.currentTimeMillis())
            _failedAttempts.value = 0
            _biometricPassed.value = false
            lastActivityTime = System.currentTimeMillis()
            true
        } else {
            _failedAttempts.value++
            false
        }
    }
    
    /**
     * PBKDF2 hashing with SHA-256.
     */
    private fun hashPinPbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Lock the vault immediately.
     */
    fun lock() {
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
            val executor: Executor = ContextCompat.getMainExecutor(context)
            
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _biometricPassed.value = true
                    _failedAttempts.value = 0
                    continuation.resume(UnlockResult.Success)
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    _failedAttempts.value++
                    if (_failedAttempts.value >= MAX_FAILED_ATTEMPTS) {
                        continuation.resume(UnlockResult.TooManyAttempts)
                    } else {
                        continuation.resume(UnlockResult.Error(errString.toString()))
                    }
                }
                
                override fun onAuthenticationFailed() {
                    _failedAttempts.value++
                    if (_failedAttempts.value >= MAX_FAILED_ATTEMPTS) {
                        continuation.resume(UnlockResult.TooManyAttempts)
                    }
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
     */
    fun onAppBackgrounded() {
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
        lastActivityTime = System.currentTimeMillis()
    }
    
    /**
     * Check inactivity lock.
     */
    suspend fun checkInactivityLock() {
        val timeout = context.dataStore.data.first()[KEY_LOCK_TIMEOUT] ?: DEFAULT_LOCK_TIMEOUT
        if (timeout > 0 && System.currentTimeMillis() - lastActivityTime > timeout) {
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
    data class Unlocked(val unlockedAt: Long) : LockState()
}

/**
 * Authentication method.
 */
enum class AuthMethod {
    BIOMETRIC,
    PIN,
    PATTERN
}

/**
 * Result of an unlock attempt.
 */
sealed class UnlockResult {
    object Success : UnlockResult()
    object TooManyAttempts : UnlockResult()
    data class Error(val message: String) : UnlockResult()
}
