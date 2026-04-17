package com.pionen.app.core.vault

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for vault metadata.
 * This database is encrypted using SQLCipher.
 * The encryption key is derived from the master key in Android Keystore.
 */
@Database(
    entities = [VaultFile::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(UUIDConverter::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultFileDao(): VaultFileDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE vault_files ADD COLUMN is_decoy INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
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
