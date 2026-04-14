package com.pionen.app.core.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileEncryptor: Encrypts and decrypts files using AES-256-GCM.
 * 
 * Security Design:
 * - AES-256-GCM provides authenticated encryption (integrity + confidentiality)
 * - Random IV (nonce) for each encryption operation
 * - 128-bit authentication tag for tamper detection
 * - Streaming encryption for large files
 * 
 * File Format:
 * [IV: 12 bytes][Ciphertext: N bytes][Auth Tag: 16 bytes (appended by GCM)]
 */
@Singleton
class FileEncryptor @Inject constructor(
    private val keyManager: KeyManager
) {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // 96 bits for GCM
        private const val TAG_SIZE = 128 // bits
        private const val BUFFER_SIZE = 8192 // 8KB chunks
    }
    
    /**
     * Encrypt a byte array to a file.
     * 
     * @param data The plaintext data to encrypt
     * @param fileId The unique file identifier (used to get/create key)
     * @param outputFile The destination file for encrypted content
     * @return EncryptionResult with metadata
     */
    fun encryptToFile(data: ByteArray, fileId: UUID, outputFile: File): EncryptionResult {
        val key = keyManager.getFileKey(fileId) ?: keyManager.generateFileKey(fileId)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv // GCM generates random IV
        val ciphertext = cipher.doFinal(data)
        
        // Write IV + ciphertext
        FileOutputStream(outputFile).use { fos ->
            fos.write(iv)
            fos.write(ciphertext)
        }
        
        return EncryptionResult(
            fileId = fileId,
            outputFile = outputFile,
            originalSize = data.size.toLong(),
            encryptedSize = outputFile.length()
        )
    }
    
    /**
     * Encrypt an input stream directly to a file.
     * Plaintext never fully exists in memory.
     * 
     * @param inputStream The source of plaintext data
     * @param fileId The unique file identifier
     * @param outputFile The destination file
     * @return EncryptionResult with metadata
     */
    fun encryptStream(inputStream: InputStream, fileId: UUID, outputFile: File): EncryptionResult {
        val key = keyManager.getFileKey(fileId) ?: keyManager.generateFileKey(fileId)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        var totalBytes = 0L
        
        FileOutputStream(outputFile).use { fos ->
            // Write IV first
            fos.write(iv)
            
            // Stream encrypt
            CipherOutputStream(fos, cipher).use { cos ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    cos.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
            }
        }
        
        return EncryptionResult(
            fileId = fileId,
            outputFile = outputFile,
            originalSize = totalBytes,
            encryptedSize = outputFile.length()
        )
    }
    
    /**
     * Decrypt a file to a SecureBuffer (RAM only).
     * 
     * @param fileId The file identifier to get the decryption key
     * @param encryptedFile The encrypted file to decrypt
     * @return SecureBuffer containing decrypted content
     * @throws SecurityException if decryption fails (tampering detected)
     */
    fun decryptToMemory(fileId: UUID, encryptedFile: File): SecureBuffer {
        val key = keyManager.getFileKey(fileId)
            ?: throw SecurityException("Decryption key not found for file: $fileId")
        
        FileInputStream(encryptedFile).use { fis ->
            // Read IV
            val iv = ByteArray(IV_SIZE)
            val ivRead = fis.read(iv)
            if (ivRead != IV_SIZE) {
                throw SecurityException("Invalid encrypted file format")
            }
            
            // Decrypt
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            val ciphertext = fis.readBytes()
            val plaintext = cipher.doFinal(ciphertext)
            
            return SecureBuffer.wrap(plaintext)
        }
    }
    
    /**
     * Get a decrypting InputStream for streaming large files.
     * Caller is responsible for closing the stream.
     * 
     * @param fileId The file identifier
     * @param encryptedFile The encrypted file
     * @return A CipherInputStream that decrypts on read
     */
    fun decryptStream(fileId: UUID, encryptedFile: File): InputStream {
        val key = keyManager.getFileKey(fileId)
            ?: throw SecurityException("Decryption key not found for file: $fileId")
        
        val fis = FileInputStream(encryptedFile)
        
        // Read IV — validate full read to avoid silent zero-pad corruption
        val iv = ByteArray(IV_SIZE)
        val ivRead = fis.read(iv)
        if (ivRead != IV_SIZE) {
            fis.close()
            throw SecurityException("Invalid encrypted file format: IV truncated ($ivRead/$IV_SIZE bytes)")
        }
        
        // Create decrypting stream
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return CipherInputStream(fis, cipher)
    }
    
    /**
     * Create an encrypting output stream for direct-to-vault writes.
     * Writes IV first, then encrypted content.
     * Caller must close the stream.
     * 
     * @param fileId The file identifier
     * @param outputFile The output encrypted file
     * @return An OutputStream that encrypts on write
     */
    fun createEncryptingStream(fileId: UUID, outputFile: File): OutputStream {
        val key = keyManager.getFileKey(fileId) ?: keyManager.generateFileKey(fileId)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val fos = FileOutputStream(outputFile)
        
        // Write IV first
        fos.write(cipher.iv)
        
        return CipherOutputStream(fos, cipher)
    }
}

/**
 * Result of an encryption operation.
 */
data class EncryptionResult(
    val fileId: UUID,
    val outputFile: File,
    val originalSize: Long,
    val encryptedSize: Long
)
