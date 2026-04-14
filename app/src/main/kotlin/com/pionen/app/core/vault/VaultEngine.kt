package com.pionen.app.core.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.pionen.app.core.crypto.FileEncryptor
import com.pionen.app.core.crypto.KeyManager
import com.pionen.app.core.crypto.SecureBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VaultEngine: Central orchestrator for all vault operations.
 * 
 * Security Design:
 * - All file content is encrypted before storage
 * - Deletion is crypto-shredding (key destruction)
 * - No plaintext ever touches disk
 * - Metadata stored in encrypted SQLCipher database
 */
@Singleton
class VaultEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
    private val fileEncryptor: FileEncryptor,
    private val vaultFileDao: VaultFileDao
) {
    
    private val vaultDirectory: File by lazy {
        File(context.filesDir, "vault").apply { mkdirs() }
    }
    
    /**
     * Get all files in the vault as a Flow.
     */
    fun getAllFiles(): Flow<List<VaultFile>> = vaultFileDao.getAllFiles()
    
    /**
     * Get recent files.
     */
    fun getRecentFiles(limit: Int = 10): Flow<List<VaultFile>> = 
        vaultFileDao.getRecentFiles(limit)
    
    /**
     * Search files by name.
     */
    fun searchFiles(query: String): Flow<List<VaultFile>> = 
        vaultFileDao.searchFiles(query)
    
    /**
     * Get a specific file.
     */
    suspend fun getFile(fileId: UUID): VaultFile? = vaultFileDao.getFile(fileId)
    
    /**
     * Import an external file into the vault.
     * Shows warning about external file security limitations.
     * 
     * @param sourceUri The content:// URI of the external file
     * @return The imported VaultFile
     */
    suspend fun importFile(sourceUri: Uri): VaultFile = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        
        // Extract file metadata
        val fileName = getFileName(sourceUri) ?: "imported_file"
        val mimeType = contentResolver.getType(sourceUri) ?: "application/octet-stream"
        
        // Generate new file ID
        val fileId = UUID.randomUUID()
        val encryptedFile = File(vaultDirectory, "$fileId.enc")
        
        // Stream encrypt directly
        val result = contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            fileEncryptor.encryptStream(inputStream, fileId, encryptedFile)
        } ?: throw IllegalStateException("Could not open file for import")
        
        // Create metadata record
        val vaultFile = VaultFile(
            id = fileId,
            fileName = fileName,
            mimeType = mimeType,
            originalSize = result.originalSize,
            encryptedSize = result.encryptedSize,
            isImported = true // Mark as imported for warning purposes
        )
        
        vaultFileDao.insert(vaultFile)
        vaultFile
    }
    
    /**
     * Create a new file directly in the vault.
     * This is the most secure option as content is never unencrypted on disk.
     * 
     * @param content The plaintext content
     * @param fileName The display name
     * @param mimeType The MIME type
     * @return The created VaultFile
     */
    suspend fun createFile(
        content: ByteArray,
        fileName: String,
        mimeType: String
    ): VaultFile = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID()
        val encryptedFile = File(vaultDirectory, "$fileId.enc")
        
        val result = fileEncryptor.encryptToFile(content, fileId, encryptedFile)
        
        val vaultFile = VaultFile(
            id = fileId,
            fileName = fileName,
            mimeType = mimeType,
            originalSize = result.originalSize,
            encryptedSize = result.encryptedSize,
            isImported = false
        )
        
        vaultFileDao.insert(vaultFile)
        vaultFile
    }
    
    /**
     * Create a file from an input stream.
     * Stream is encrypted directly without buffering entire content.
     */
    suspend fun createFileFromStream(
        inputStream: InputStream,
        fileName: String,
        mimeType: String
    ): VaultFile = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID()
        val encryptedFile = File(vaultDirectory, "$fileId.enc")
        
        val result = fileEncryptor.encryptStream(inputStream, fileId, encryptedFile)
        
        val vaultFile = VaultFile(
            id = fileId,
            fileName = fileName,
            mimeType = mimeType,
            originalSize = result.originalSize,
            encryptedSize = result.encryptedSize,
            isImported = false
        )
        
        vaultFileDao.insert(vaultFile)
        vaultFile
    }
    
    /**
     * Open a file for viewing/editing.
     * Content is decrypted to RAM only.
     * 
     * @param fileId The file to open
     * @return DecryptedContent with the SecureBuffer
     */
    suspend fun openFile(fileId: UUID): DecryptedContent = withContext(Dispatchers.IO) {
        val vaultFile = vaultFileDao.getFile(fileId)
            ?: throw IllegalArgumentException("File not found: $fileId")
        
        val encryptedFile = File(vaultDirectory, vaultFile.encryptedFileName)
        if (!encryptedFile.exists()) {
            throw IllegalStateException("Encrypted file missing: $fileId")
        }
        
        val buffer = fileEncryptor.decryptToMemory(fileId, encryptedFile)
        
        DecryptedContent(
            file = vaultFile,
            buffer = buffer
        )
    }
    
    /**
     * Get an input stream for streaming large files.
     */
    suspend fun getFileStream(fileId: UUID): InputStream = withContext(Dispatchers.IO) {
        val vaultFile = vaultFileDao.getFile(fileId)
            ?: throw IllegalArgumentException("File not found: $fileId")
        
        val encryptedFile = File(vaultDirectory, vaultFile.encryptedFileName)
        fileEncryptor.decryptStream(fileId, encryptedFile)
    }
    
    /**
     * CRYPTO-SHRED: Delete a file by destroying its encryption key.
     * After this operation, the file is cryptographically irrecoverable.
     * 
     * @param fileId The file to delete
     * @return DeletionResult with status
     */
    suspend fun deleteFile(fileId: UUID): DeletionResult = withContext(Dispatchers.IO) {
        val vaultFile = vaultFileDao.getFile(fileId)
        
        // Step 1: Destroy the encryption key (CRITICAL)
        val keyDestroyed = keyManager.destroyKey(fileId)
        
        // Step 2: Delete encrypted blob (optional, for storage reclaim)
        val encryptedFile = File(vaultDirectory, "$fileId.enc")
        val blobDeleted = if (encryptedFile.exists()) {
            encryptedFile.delete()
        } else true
        
        // Step 3: Delete thumbnail if exists
        val thumbnailFile = File(vaultDirectory, "$fileId.thumb.enc")
        if (thumbnailFile.exists()) {
            thumbnailFile.delete()
        }
        
        // Step 4: Remove metadata
        vaultFileDao.deleteById(fileId)
        
        DeletionResult(
            fileId = fileId,
            keyDestroyed = keyDestroyed,
            blobDeleted = blobDeleted,
            isIrrecoverable = keyDestroyed
        )
    }
    
    /**
     * PANIC WIPE: Destroy all encryption keys.
     * All files become irrecoverable.
     * 
     * @return PanicWipeResult with statistics
     */
    suspend fun panicWipe(): PanicWipeResult = withContext(Dispatchers.IO) {
        val fileCount = vaultFileDao.getFileCount()
        
        // Step 1: Destroy ALL keys (CRITICAL)
        val keysDestroyed = keyManager.destroyAllKeys()
        
        // Step 2: Clear metadata
        vaultFileDao.deleteAll()
        
        // Step 3: Delete encrypted files (optional, for storage)
        val vaultFiles = vaultDirectory.listFiles() ?: emptyArray()
        var blobsDeleted = 0
        for (file in vaultFiles) {
            if (file.delete()) blobsDeleted++
        }
        
        PanicWipeResult(
            totalFiles = fileCount,
            keysDestroyed = keysDestroyed,
            blobsDeleted = blobsDeleted,
            isComplete = keysDestroyed >= fileCount
        )
    }
    
    /**
     * Get vault statistics.
     */
    suspend fun getVaultStats(): VaultStats = withContext(Dispatchers.IO) {
        VaultStats(
            fileCount = vaultFileDao.getFileCount(),
            totalOriginalSize = vaultFileDao.getTotalOriginalSize() ?: 0L,
            vaultDirectorySize = calculateDirectorySize(vaultDirectory)
        )
    }
    
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName ?: uri.lastPathSegment
    }
    
    private fun calculateDirectorySize(directory: File): Long {
        return directory.listFiles()?.sumOf { it.length() } ?: 0L
    }
}

/**
 * Decrypted file content - lives only in RAM.
 */
data class DecryptedContent(
    val file: VaultFile,
    val buffer: SecureBuffer
)

/**
 * Result of a file deletion (crypto-shred).
 */
data class DeletionResult(
    val fileId: UUID,
    val keyDestroyed: Boolean,
    val blobDeleted: Boolean,
    val isIrrecoverable: Boolean
)

/**
 * Result of a panic wipe operation.
 */
data class PanicWipeResult(
    val totalFiles: Int,
    val keysDestroyed: Int,
    val blobsDeleted: Int,
    val isComplete: Boolean
)

/**
 * Vault statistics.
 */
data class VaultStats(
    val fileCount: Int,
    val totalOriginalSize: Long,
    val vaultDirectorySize: Long
)
