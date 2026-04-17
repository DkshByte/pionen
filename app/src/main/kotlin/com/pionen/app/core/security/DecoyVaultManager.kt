package com.pionen.app.core.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import com.pionen.app.core.crypto.KeyManager

private val Context.decoyDataStore by preferencesDataStore(name = "decoy_vault_prefs")

/**
 * DecoyVaultManager: Manages a fake vault that appears when entering a decoy PIN.
 * 
 * Security Design:
 * - Decoy PIN unlocks a fake vault with innocent-looking files
 * - Real vault only accessible with the real PIN
 * - Attacker cannot distinguish between decoy and real vault
 * - All decoy operations are logged covertly
 */
@Singleton
class DecoyVaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lockManager: LockManager,
    private val keyManager: KeyManager
) {
    
    companion object {
        private val KEY_DECOY_ENABLED = booleanPreferencesKey("decoy_enabled")
        private val KEY_DECOY_PIN_HASH = stringPreferencesKey("decoy_pin_hash")
        private val KEY_DECOY_PIN_SALT = stringPreferencesKey("decoy_pin_salt")
        private val KEY_DECOY_ACCESS_COUNT = stringPreferencesKey("decoy_access_count")
        
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 256
    }
    
    /**
     * Check if decoy vault is enabled.
     */
    val isDecoyEnabled: Flow<Boolean> = context.decoyDataStore.data.map { prefs ->
        prefs[KEY_DECOY_ENABLED] ?: false
    }
    
    /**
     * Enable decoy vault with a separate PIN.
     * 
     * @throws IllegalArgumentException if the decoy PIN matches the real vault PIN.
     */
    suspend fun enableDecoyVault(decoyPin: String) {
        require(decoyPin.length == 6 && decoyPin.all { it.isDigit() }) { "Decoy PIN must be 6 digits" }
        
        // SECURITY: Reject if the decoy PIN matches the real vault PIN.
        // This prevents a collision that would permanently route the user
        // into the decoy vault, locking them out of their real data.
        require(!lockManager.verifyPinWithoutUnlock(decoyPin)) {
            "Decoy PIN must be different from the real vault PIN"
        }
        
        // Generate salt
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        
        // Hash with PBKDF2
        val hash = hashPinPbkdf2(decoyPin, salt)
        
        context.decoyDataStore.edit { prefs ->
            prefs[KEY_DECOY_ENABLED] = true
            prefs[KEY_DECOY_PIN_HASH] = hash
            prefs[KEY_DECOY_PIN_SALT] = saltHex
        }
    }
    
    /**
     * Disable decoy vault.
     */
    suspend fun disableDecoyVault() {
        context.decoyDataStore.edit { prefs ->
            prefs[KEY_DECOY_ENABLED] = false
            prefs.remove(KEY_DECOY_PIN_HASH)
            prefs.remove(KEY_DECOY_PIN_SALT)
        }
    }
    
    /**
     * Check if the entered PIN is the decoy PIN.
     * Returns true if this is a decoy access (should show fake vault).
     */
    suspend fun isDecoyPin(pin: String): Boolean {
        val prefs = context.decoyDataStore.data.first()
        
        if (prefs[KEY_DECOY_ENABLED] != true) return false
        
        val storedHash = prefs[KEY_DECOY_PIN_HASH] ?: return false
        val saltHex = prefs[KEY_DECOY_PIN_SALT] ?: return false
        
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val inputHash = hashPinPbkdf2(pin, salt)
        
        val isValid = java.security.MessageDigest.isEqual(
            inputHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
        
        if (isValid) {
            // Log covert access (increment counter)
            recordDecoyAccess()
            return true
        }
        
        return false
    }
    
    /**
     * Record that decoy vault was accessed (for covert logging).
     */
    private suspend fun recordDecoyAccess() {
        context.decoyDataStore.edit { prefs ->
            val currentCount = (prefs[KEY_DECOY_ACCESS_COUNT]?.toIntOrNull() ?: 0) + 1
            prefs[KEY_DECOY_ACCESS_COUNT] = currentCount.toString()
        }
    }
    
    /**
     * Get decoy access count (for security audit).
     */
    suspend fun getDecoyAccessCount(): Int {
        val prefs = context.decoyDataStore.data.first()
        return prefs[KEY_DECOY_ACCESS_COUNT]?.toIntOrNull() ?: 0
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
}

/**
 * Decoy vault state.
 */
sealed class DecoyVaultState {
    object Disabled : DecoyVaultState()
    object Enabled : DecoyVaultState()
    object InDecoyMode : DecoyVaultState() // Currently viewing decoy vault
}
