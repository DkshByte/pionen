package com.pionen.app.core.vault

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

/**
 * Room database for vault metadata.
 * This database is encrypted using SQLCipher.
 * The encryption key is derived from the master key in Android Keystore.
 */
@Database(
    entities = [VaultFile::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(UUIDConverter::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultFileDao(): VaultFileDao
}

/**
 * Type converter for UUID <-> String.
 */
class UUIDConverter {
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? {
        return uuid?.toString()
    }
    
    @TypeConverter
    fun toUUID(value: String?): UUID? {
        return value?.let { UUID.fromString(it) }
    }
}
