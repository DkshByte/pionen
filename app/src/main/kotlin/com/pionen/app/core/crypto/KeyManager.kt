package com.pionen.app.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.app.KeyguardManager
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * KeyManager: Manages all encryption keys using Android Keystore.
 * 
 * Security Design:
 * - Each file gets a unique AES-256 key
 * - Keys are stored in hardware-backed Keystore (TEE/StrongBox when available)
 * - Keys are non-extractable - never leave secure hardware
 * - Crypto-shredding: file deletion = key destruction
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "pionen_file_"
        private const val PIN_MAC_KEY_ALIAS = "pionen_pin_mac"
        private const val KEY_SIZE = 256
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    
    /**
     * Generate a unique AES-256 key for a file.
     * Key is stored in Android Keystore.
     * 
     * @param fileId Unique identifier for the file
     * @return The generated SecretKey
     */
    fun generateFileKey(fileId: UUID): SecretKey {
        val alias = getKeyAlias(fileId)
        
        // Try with StrongBox first (Android 9+), fall back to TEE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKeyWithSpec(alias, useStrongBox = true)
            } catch (e: Exception) {
                // StrongBox not available or failed, fall back to TEE
            }
        }
        
        // Generate with TEE (software-backed on older devices)
        return generateKeyWithSpec(alias, useStrongBox = false)
    }
    
    private fun generateKeyWithSpec(alias: String, useStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        
        // Use StrongBox if requested and available (Android 9+)
        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        
        // SECURITY: Prevent catastrophic data loss if user adds a new fingerprint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(false)
        }
        
        // Bind to Device Lock Screen if available
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isDeviceSecure) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    300, 
                    KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(300)
            }
        }
        
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
    
    /**
     * Hardware-bound HMAC wrapper for PIN hashes.
     * Prevents offline brute-force extraction attacks.
     */
    fun hashWithHardwareMac(input: ByteArray): ByteArray {
        val macKey = try {
            val key = keyStore.getKey(PIN_MAC_KEY_ALIAS, null) as? SecretKey
            if (key == null) {
                // Initialize the hardware MAC key on first use
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    ANDROID_KEYSTORE
                )
                val builder = KeyGenParameterSpec.Builder(
                    PIN_MAC_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setInvalidatedByBiometricEnrollment(false)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        keyGenerator.init(builder.setIsStrongBoxBacked(true).build())
                        keyGenerator.generateKey()
                    } catch (e: Exception) {
                        keyGenerator.init(builder.setIsStrongBoxBacked(false).build())
                        keyGenerator.generateKey()
                    }
                } else {
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                }
            } else {
                key
            }
        } catch (e: Exception) {
            null
        }
        
        if (macKey != null) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(macKey)
            return mac.doFinal(input)
        }
        // SECURITY: Never silently degrade — without hardware HMAC, the PIN hash
        // becomes a pure PBKDF2 output that is trivially brute-forceable offline.
        throw SecurityException("Hardware-backed MAC unavailable — cannot securely hash PIN")
    }
    
    /**
     * Retrieve an existing key from Keystore.
     * 
     * @param fileId The file's unique identifier
     * @return The SecretKey if it exists, null otherwise
     */
    fun getFileKey(fileId: UUID): SecretKey? {
        val alias = getKeyAlias(fileId)
        return keyStore.getKey(alias, null) as? SecretKey
    }
    
    /**
     * Check if a key exists for a file.
     */
    fun hasKey(fileId: UUID): Boolean {
        val alias = getKeyAlias(fileId)
        return keyStore.containsAlias(alias)
    }
    
    /**
     * CRYPTO-SHRED: Permanently destroy the encryption key.
     * Once destroyed, the associated file is cryptographically irrecoverable.
     * 
     * @param fileId The file whose key should be destroyed
     * @return true if the key was successfully destroyed
     */
    fun destroyKey(fileId: UUID): Boolean {
        val alias = getKeyAlias(fileId)
        return try {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                !keyStore.containsAlias(alias) // Verify deletion
            } else {
                true // Key already doesn't exist
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Destroy ALL keys - panic wipe.
     * 
     * @return Number of keys destroyed
     */
    fun destroyAllKeys(): Int {
        var destroyed = 0
        val aliases = keyStore.aliases().toList()
        
        for (alias in aliases) {
            if (alias.startsWith(KEY_ALIAS_PREFIX) || alias == PIN_MAC_KEY_ALIAS) {
                try {
                    keyStore.deleteEntry(alias)
                    destroyed++
                } catch (e: Exception) {
                    // Continue with other keys
                }
            }
        }
        
        return destroyed
    }
    
    /**
     * Check if a key is hardware-backed.
     * 
     * @param fileId The file whose key to check
     * @return KeyProtectionInfo describing the key's protection level
     */
    fun getKeyProtectionInfo(fileId: UUID): KeyProtectionInfo {
        val alias = getKeyAlias(fileId)
        val key = keyStore.getKey(alias, null) as? SecretKey
            ?: return KeyProtectionInfo(exists = false)
        
        return try {
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            
            KeyProtectionInfo(
                exists = true,
                isHardwareBacked = keyInfo.isInsideSecureHardware,
                isStrongBoxBacked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
                } else {
                    false
                }
            )
        } catch (e: Exception) {
            KeyProtectionInfo(exists = true, isHardwareBacked = false)
        }
    }
    
    /**
     * Get all file IDs that have keys in the Keystore.
     */
    fun getAllFileIds(): List<UUID> {
        return keyStore.aliases().toList()
            .filter { it.startsWith(KEY_ALIAS_PREFIX) }
            .mapNotNull { alias ->
                try {
                    UUID.fromString(alias.removePrefix(KEY_ALIAS_PREFIX))
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    private fun getKeyAlias(fileId: UUID): String = "$KEY_ALIAS_PREFIX$fileId"
}

/**
 * Information about key protection level.
 */
data class KeyProtectionInfo(
    val exists: Boolean,
    val isHardwareBacked: Boolean = false,
    val isStrongBoxBacked: Boolean = false
)
