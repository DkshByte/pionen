package com.pionen.app.core.vault

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * VaultFile: Represents a file stored in the encrypted vault.
 * 
 * Note: Only metadata is stored in the database.
 * The actual encrypted content is stored as a separate file.
 */
@Entity(tableName = "vault_files")
data class VaultFile(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    
    @ColumnInfo(name = "file_name")
    val fileName: String,
    
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    
    @ColumnInfo(name = "original_size")
    val originalSize: Long,
    
    @ColumnInfo(name = "encrypted_size")
    val encryptedSize: Long,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_imported")
    val isImported: Boolean = false, // True if imported from external source
    
    @ColumnInfo(name = "thumbnail_available")
    val thumbnailAvailable: Boolean = false,
    
    @ColumnInfo(name = "is_decoy")
    val isDecoy: Boolean = false
) {
    /**
     * Get the encrypted file name (stored on disk).
     */
    val encryptedFileName: String
        get() = "$id.enc"
    
    /**
     * Get the encrypted thumbnail file name.
     */
    val thumbnailFileName: String
        get() = "$id.thumb.enc"
    
    /**
     * Human-readable file size.
     */
    val formattedSize: String
        get() = formatFileSize(originalSize)
    
    companion object {
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}

/**
 * File type categories for display/handling.
 */
enum class FileType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    PDF,
    TEXT,
    UNKNOWN;
    
    companion object {
        fun fromMimeType(mimeType: String): FileType {
            return when {
                mimeType.startsWith("image/") -> IMAGE
                mimeType.startsWith("video/") -> VIDEO
                mimeType.startsWith("audio/") -> AUDIO
                mimeType == "application/pdf" -> PDF
                mimeType.startsWith("text/") -> TEXT
                mimeType.contains("document") || 
                mimeType.contains("sheet") ||
                mimeType.contains("presentation") -> DOCUMENT
                else -> UNKNOWN
            }
        }
    }
}
