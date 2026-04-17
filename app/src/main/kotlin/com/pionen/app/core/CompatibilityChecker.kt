package com.pionen.app.core

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks whether the device meets all hardware/software requirements
 * for Pionen to operate securely.
 *
 * Requirements:
 *  - Android 8.0+ (API 26) — already enforced by minSdk, but validated here for UX messaging
 *  - Android KeyStore backed by hardware TEE / StrongBox (no software-only key storage)
 *  - AES-256 GCM support
 */
@Singleton
class CompatibilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class CompatibilityResult(
        val isCompatible: Boolean,
        val failedReasons: List<String>
    )

    /**
     * Run all compatibility checks. Returns a result with a list of human-readable
     * failure reasons if any requirement is not met.
     */
    fun check(): CompatibilityResult {
        val failures = mutableListOf<String>()

        // 1. Minimum Android version
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            failures += "Requires Android 8.0 (Oreo) or later. Your device runs Android ${Build.VERSION.RELEASE}."
        }

        // 2. Hardware-backed Android KeyStore
        if (!isHardwareKeyStoreAvailable()) {
            failures += "Hardware-backed secure key storage (TEE) is not available on this device."
        }

        // 3. AES-256 support
        if (!isAesSupported()) {
            failures += "AES-256 encryption is not supported on this device."
        }

        return CompatibilityResult(
            isCompatible = failures.isEmpty(),
            failedReasons = failures
        )
    }

    /**
     * Checks that the device has a hardware-backed Android KeyStore.
     * Generates a test AES key (matching Pionen's real key type) and
     * inspects [KeyInfo.isInsideSecureHardware] to verify TEE/StrongBox backing.
     */
    private fun isHardwareKeyStoreAvailable(): Boolean {
        val alias = "pionen_compat_test_${System.currentTimeMillis()}"
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            // Generate a test AES key — same type Pionen uses for file encryption
            val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            val key = keyGenerator.generateKey()

            // Inspect the key via SecretKeyFactory → KeyInfo
            val factory = javax.crypto.SecretKeyFactory.getInstance(
                key.algorithm, "AndroidKeyStore"
            )
            val keyInfo = factory.getKeySpec(
                key, android.security.keystore.KeyInfo::class.java
            ) as android.security.keystore.KeyInfo

            keyInfo.isInsideSecureHardware
        } catch (e: Exception) {
            false
        } finally {
            // Clean up test key
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                ks.deleteEntry(alias)
            } catch (_: Exception) {}
        }
    }

    /**
     * Checks that AES-256/GCM/NoPadding is available via the Android KeyStore.
     */
    private fun isAesSupported(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val alias = "pionen_aes_compat_${System.currentTimeMillis()}"
            val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()

            // Clean up
            try { keyStore.deleteEntry(alias) } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            false
        }
    }
}
