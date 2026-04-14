package com.pionen.app.di

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pionen.app.core.vault.VaultDatabase
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.core.vault.VaultFileDao
import com.pionen.app.server.SecureWebServer
import com.pionen.app.core.network.TorManager
import com.pionen.app.core.network.VpnStatusManager
import com.pionen.app.core.network.ProxyAwareHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * Hilt module providing app-wide dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provide encrypted database using SQLCipher.
     * 
     * Security Design:
     * - Database encrypted with SQLCipher
     * - Encryption key derived from Android Keystore
     * - All file metadata encrypted at rest
     */
    @Provides
    @Singleton
    fun provideVaultDatabase(
        @ApplicationContext context: Context,
        databaseKeyProvider: DatabaseKeyProvider
    ): VaultDatabase {
        val passphrase = databaseKeyProvider.getDatabaseKey()
        val factory = SupportFactory(passphrase)
        
        fun buildDb(): VaultDatabase = Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "pionen_vault.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
        
        val db = buildDb()
        
        // Validate that the database can actually be opened with this key.
        // If the key changed (e.g. EncryptedSharedPreferences was reset),
        // SQLCipher will throw "file is not a database". In that case,
        // delete the stale DB file and recreate. The DB only holds metadata;
        // the actual encrypted vault files on disk are unaffected.
        return try {
            db.openHelper.writableDatabase
            db
        } catch (e: Exception) {
            android.util.Log.e("AppModule", "Database key mismatch – resetting DB", e)
            try { db.close() } catch (_: Exception) {}
            val dbFile = context.getDatabasePath("pionen_vault.db")
            dbFile.delete()
            java.io.File("${dbFile.path}-journal").delete()
            java.io.File("${dbFile.path}-wal").delete()
            java.io.File("${dbFile.path}-shm").delete()
            buildDb()
        }
    }
    
    @Provides
    @Singleton
    fun provideVaultFileDao(database: VaultDatabase): VaultFileDao {
        return database.vaultFileDao()
    }
    
    /**
     * Provide SecureWebServer singleton for local file hosting.
     */
    @Provides
    @Singleton
    fun provideSecureWebServer(
        @ApplicationContext context: Context,
        vaultEngine: VaultEngine
    ): SecureWebServer {
        return SecureWebServer(context, vaultEngine)
    }
}

/**
 * Provides the database encryption key.
 *
 * Security Design:
 * - Key is stored in [EncryptedSharedPreferences], which wraps it with an
 *   AES-256-GCM key held in the Android Keystore. The raw 256-bit DB key
 *   never exists as plaintext outside of app memory.
 * - On first launch a cryptographically random key is generated and persisted.
 * - The master key (`pionen_db_master`) is hardware-backed on supported devices.
 */
@Singleton
class DatabaseKeyProvider @javax.inject.Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DATABASE_KEY_PREF_NAME = "pionen_db_key_store"
        private const val DATABASE_KEY_ALIAS = "pionen_db_key"
        private const val MASTER_KEY_ALIAS = "pionen_db_master"
        private const val KEY_SIZE = 32 // 256 bits
    }

    /**
     * Returns the 256-bit SQLCipher database passphrase.
     * Creates and securely persists a new one on first call.
     */
    fun getDatabaseKey(): ByteArray {
        return try {
            val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                DATABASE_KEY_PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val existingKey = encryptedPrefs.getString(DATABASE_KEY_ALIAS, null)
            if (existingKey != null) {
                android.util.Base64.decode(existingKey, android.util.Base64.DEFAULT)
            } else {
                val newKey = ByteArray(KEY_SIZE)
                java.security.SecureRandom().nextBytes(newKey)
                encryptedPrefs.edit()
                    .putString(
                        DATABASE_KEY_ALIAS,
                        android.util.Base64.encodeToString(newKey, android.util.Base64.DEFAULT)
                    )
                    .apply()
                newKey
            }
        } catch (e: Exception) {
            // Last-resort ephemeral key: vault is inaccessible after reinstall,
            // but files are not exposed. Log the failure for diagnostics.
            android.util.Log.e("DatabaseKeyProvider", "Failed to access encrypted key store", e)
            ByteArray(KEY_SIZE).also { java.security.SecureRandom().nextBytes(it) }
        }
    }
}
