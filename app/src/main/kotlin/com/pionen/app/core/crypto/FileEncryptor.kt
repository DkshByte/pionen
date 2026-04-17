package com.pionen.app.core.crypto

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileEncryptor: Encrypts and decrypts files using AES-256-GCM with chunked AEAD.
 * 
 * Security Design:
 * - AES-256-GCM provides authenticated encryption (integrity + confidentiality)
 * - Random IV (nonce) for each encryption operation
 * - 128-bit authentication tag for tamper detection
 * - **Chunked AEAD** for streaming: each chunk is independently authenticated,
 *   preventing the OOM and unauthenticated-data-release vulnerabilities
 *   inherent in raw GCM + CipherInputStream/CipherOutputStream.
 * 
 * File Format (single-shot, small files):
 *   [IV: 12 bytes][Ciphertext + Auth Tag: N bytes]
 * 
 * File Format (chunked streaming, large files):
 *   [MAGIC: 4 bytes "PCAE"][VERSION: 1 byte][IV_PREFIX: 8 bytes]
 *   [CHUNK_SIZE: 4 bytes (big-endian)]
 *   [CHUNK_0: 4-byte len || ciphertext+tag] ...
 *   [FINAL_CHUNK: 4-byte len || ciphertext+tag]
 *
 * Each chunk gets a unique 12-byte nonce derived from the 8-byte IV prefix
 * concatenated with a 4-byte big-endian chunk counter, preventing nonce reuse.
 */
@Singleton
class FileEncryptor @Inject constructor(
    private val keyManager: KeyManager
) {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12      // 96 bits for GCM
        private const val IV_PREFIX_SIZE = 8 // 8 bytes prefix + 4 bytes counter = 12
        private const val TAG_SIZE = 128     // bits
        private const val CHUNK_PLAINTEXT_SIZE = 65536 // 64KB per chunk
        
        // Magic bytes to identify chunked AEAD format
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte(), 'E'.code.toByte())
        private const val FORMAT_VERSION: Byte = 1
    }
    
    /**
     * Encrypt a byte array to a file.
     * Uses single-shot GCM for small payloads (safe since data is already in memory).
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
        
        val tmpFile = File(outputFile.absolutePath + ".tmp")
        try {
            FileOutputStream(tmpFile).use { fos ->
                fos.write(iv)
                fos.write(ciphertext)
            }
            if (!tmpFile.renameTo(outputFile)) {
                tmpFile.copyTo(outputFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
        
        return EncryptionResult(
            fileId = fileId,
            outputFile = outputFile,
            originalSize = data.size.toLong(),
            encryptedSize = outputFile.length()
        )
    }
    
    /**
     * Encrypt an input stream directly to a file using chunked AEAD.
     * Each 64KB chunk is independently encrypted and authenticated.
     * Plaintext never fully exists in memory.
     * 
     * @param inputStream The source of plaintext data
     * @param fileId The unique file identifier
     * @param outputFile The destination file
     * @return EncryptionResult with metadata
     */
    fun encryptStream(inputStream: InputStream, fileId: UUID, outputFile: File): EncryptionResult {
        val key = keyManager.getFileKey(fileId) ?: keyManager.generateFileKey(fileId)
        
        // Generate random IV prefix (8 bytes); each chunk nonce = prefix || counter
        val ivPrefix = ByteArray(IV_PREFIX_SIZE)
        SecureRandom().nextBytes(ivPrefix)
        
        val tmpFile = java.io.File(outputFile.absolutePath + ".tmp")
        var totalPlaintextBytes = 0L
        
        try {
            java.io.FileOutputStream(tmpFile).use { fos ->
                // Write header: MAGIC + VERSION + IV_PREFIX + CHUNK_SIZE
                fos.write(MAGIC)
                fos.write(FORMAT_VERSION.toInt())
                fos.write(ivPrefix)
                fos.write(java.nio.ByteBuffer.allocate(4).putInt(CHUNK_PLAINTEXT_SIZE).array())
                
                val buffer = ByteArray(CHUNK_PLAINTEXT_SIZE)
                var chunkCounter = 0
                
                while (true) {
                    // Read up to one full chunk of plaintext
                    val bytesRead = readFullyFromStream(inputStream, buffer)
                    if (bytesRead <= 0) break
                    
                    totalPlaintextBytes += bytesRead
                    
                    // Derive per-chunk nonce: ivPrefix (8 bytes) || chunkCounter (4 bytes BE)
                    val nonce = deriveChunkNonce(ivPrefix, chunkCounter)
                    
                    // Encrypt this chunk
                    val cipher = javax.crypto.Cipher.getInstance(ALGORITHM)
                    val spec = javax.crypto.spec.GCMParameterSpec(TAG_SIZE, nonce)
                    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, spec)
                    val chunkCiphertext = cipher.doFinal(buffer, 0, bytesRead)
                    
                    // Write chunk: [4-byte length][ciphertext+tag]
                    fos.write(java.nio.ByteBuffer.allocate(4).putInt(chunkCiphertext.size).array())
                    fos.write(chunkCiphertext)
                    
                    chunkCounter++
                    
                    if (chunkCounter == Int.MAX_VALUE) {
                        throw SecurityException("File too large: chunk counter overflow")
                    }
                }
            }
            
            // Atomic rename on success
            if (!tmpFile.renameTo(outputFile)) {
                tmpFile.copyTo(outputFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
        
        return EncryptionResult(
            fileId = fileId,
            outputFile = outputFile,
            originalSize = totalPlaintextBytes,
            encryptedSize = outputFile.length()
        )
    }
    
    /**
     * Decrypt a file to a SecureBuffer (RAM only).
     * Supports both legacy single-shot format and chunked AEAD format.
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
            // Peek at first 4 bytes to detect format
            val header = ByteArray(4)
            val headerRead = fis.read(header)
            if (headerRead < 4) {
                throw SecurityException("Invalid encrypted file format: file too short")
            }
            
            return if (header.contentEquals(MAGIC)) {
                // Chunked AEAD format
                decryptChunkedToMemory(fis, key)
            } else {
                // Legacy single-shot format: header bytes are start of IV
                decryptLegacyToMemory(header, fis, key)
            }
        }
    }
    
    /**
     * Decrypt a legacy single-shot GCM file.
     * The first 4 bytes (already read as header) are part of the 12-byte IV.
     */
    private fun decryptLegacyToMemory(
        ivStart: ByteArray,
        fis: FileInputStream,
        key: SecretKey
    ): SecureBuffer {
        // Read remaining 8 bytes of IV
        val ivRest = ByteArray(IV_SIZE - ivStart.size)
        val ivRestRead = fis.read(ivRest)
        if (ivRestRead != ivRest.size) {
            throw SecurityException("Invalid encrypted file format: IV truncated")
        }
        
        val iv = ivStart + ivRest
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        val ciphertext = fis.readBytes()
        val plaintext = cipher.doFinal(ciphertext)
        
        return SecureBuffer.wrap(plaintext)
    }
    
    /**
     * Decrypt a chunked AEAD file to memory.
     * Each chunk is independently authenticated before its plaintext is accepted.
     */
    private fun decryptChunkedToMemory(
        fis: FileInputStream,
        key: SecretKey
    ): SecureBuffer {
        // Read rest of header: VERSION(1) + IV_PREFIX(8) + CHUNK_SIZE(4)
        val version = fis.read()
        if (version != FORMAT_VERSION.toInt()) {
            throw SecurityException("Unsupported chunked format version: $version")
        }
        
        val ivPrefix = ByteArray(IV_PREFIX_SIZE)
        if (fis.read(ivPrefix) != IV_PREFIX_SIZE) {
            throw SecurityException("Invalid chunked file: IV prefix truncated")
        }
        
        val chunkSizeBytes = ByteArray(4)
        if (fis.read(chunkSizeBytes) != 4) {
            throw SecurityException("Invalid chunked file: chunk size missing")
        }
        // chunkSize is stored for format validation but we don't need it for decryption
        
        val output = ByteArrayOutputStream()
        var chunkCounter = 0
        
        while (true) {
            // Read chunk length
            val lenBytes = ByteArray(4)
            val lenRead = fis.read(lenBytes)
            if (lenRead <= 0) break // End of file
            if (lenRead != 4) {
                throw SecurityException("Invalid chunked file: chunk length truncated at chunk $chunkCounter")
            }
            
            val chunkLen = ByteBuffer.wrap(lenBytes).int
            if (chunkLen <= 0 || chunkLen > CHUNK_PLAINTEXT_SIZE + 16 + 256) {
                // Max expected: plaintext + 16-byte GCM tag + margin
                throw SecurityException("Invalid chunk length: $chunkLen at chunk $chunkCounter")
            }
            
            // Read chunk ciphertext
            val chunkCiphertext = ByteArray(chunkLen)
            val chunkRead = readFullyFromStream(fis, chunkCiphertext)
            if (chunkRead != chunkLen) {
                throw SecurityException("Chunk $chunkCounter truncated: expected $chunkLen, got $chunkRead")
            }
            
            // Derive per-chunk nonce
            val nonce = deriveChunkNonce(ivPrefix, chunkCounter)
            
            // Decrypt and authenticate this chunk
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            val plaintext = try {
                cipher.doFinal(chunkCiphertext)
            } catch (e: Exception) {
                throw SecurityException("Chunk $chunkCounter failed authentication — possible tampering", e)
            }
            
            output.write(plaintext)
            chunkCounter++
        }
        
        val result = output.toByteArray()
        // Note: ByteArrayOutputStream's internal buffer cannot be reliably zeroed.
        // SecureBuffer.close() will zero the returned array when the caller is done.
        return SecureBuffer.wrap(result)
    }
    
    /**
     * Get a decrypting InputStream for streaming large files using chunked AEAD.
     * Each chunk is fully authenticated before its plaintext bytes are released.
     * Caller is responsible for closing the stream.
     * 
     * @param fileId The file identifier
     * @param encryptedFile The encrypted file
     * @return An InputStream that decrypts and authenticates per-chunk on read
     */
    fun decryptStream(fileId: UUID, encryptedFile: File): InputStream {
        val key = keyManager.getFileKey(fileId)
            ?: throw SecurityException("Decryption key not found for file: $fileId")
        
        val fis = FileInputStream(encryptedFile)
        
        // Peek at first 4 bytes to detect format
        val header = ByteArray(4)
        val headerRead = fis.read(header)
        if (headerRead < 4) {
            fis.close()
            throw SecurityException("Invalid encrypted file format: file too short")
        }
        
        return if (header.contentEquals(MAGIC)) {
            // Chunked AEAD format — return our safe chunked stream
            ChunkedAeadInputStream(fis, key)
        } else {
            // Legacy single-shot format: must load entire file for GCM auth.
            // This is safe only for small files that were encrypted with the old format.
            fis.close()
            val secureBuffer = decryptToMemory(fileId, encryptedFile)
            secureBuffer.asInputStream()
        }
    }
    
    /**
     * Create an encrypting output stream for direct-to-vault writes using chunked AEAD.
     * Writes header first, then encrypts each chunk independently.
     * Caller must close the stream.
     * 
     * @param fileId The file identifier
     * @param outputFile The output encrypted file
     * @return An OutputStream that encrypts on write using chunked AEAD
     */
    fun createEncryptingStream(fileId: UUID, outputFile: File): OutputStream {
        val key = keyManager.getFileKey(fileId) ?: keyManager.generateFileKey(fileId)
        return ChunkedAeadOutputStream(FileOutputStream(outputFile), key)
    }
    
    // ===== Utility Methods =====
    
    /**
     * Derive a unique 12-byte nonce for a chunk.
     * nonce = ivPrefix (8 bytes) || chunkCounter (4 bytes big-endian)
     */
    private fun deriveChunkNonce(ivPrefix: ByteArray, chunkCounter: Int): ByteArray {
        val nonce = ByteArray(IV_SIZE)
        System.arraycopy(ivPrefix, 0, nonce, 0, IV_PREFIX_SIZE)
        nonce[8] = (chunkCounter shr 24).toByte()
        nonce[9] = (chunkCounter shr 16).toByte()
        nonce[10] = (chunkCounter shr 8).toByte()
        nonce[11] = chunkCounter.toByte()
        return nonce
    }
    
    /**
     * Read up to buffer.size bytes, handling partial reads.
     * Returns the number of bytes actually read, or -1 if EOF immediately.
     */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = input.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return if (totalRead == 0) -1 else totalRead
    }
    
    /**
     * Read exactly buffer.size bytes from a stream.
     */
    private fun readFullyFromStream(input: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = input.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }
    
    // ===== Inner Classes =====
    
    /**
     * InputStream that reads and authenticates one chunk at a time from a chunked AEAD file.
     * Each chunk is fully decrypted and its GCM tag verified before any bytes are released.
     * This eliminates the OOM and unauthenticated-data vulnerabilities of raw GCM streams.
     */
    private inner class ChunkedAeadInputStream(
        private val source: FileInputStream,
        private val key: SecretKey
    ) : InputStream() {
        
        private val ivPrefix: ByteArray
        private var chunkCounter = 0
        private var currentChunkPlaintext: ByteArray? = null
        private var posInChunk = 0
        private var eof = false
        
        init {
            // Read rest of header: VERSION(1) + IV_PREFIX(8) + CHUNK_SIZE(4)
            val version = source.read()
            if (version != FORMAT_VERSION.toInt()) {
                source.close()
                throw SecurityException("Unsupported chunked format version: $version")
            }
            
            ivPrefix = ByteArray(IV_PREFIX_SIZE)
            if (source.read(ivPrefix) != IV_PREFIX_SIZE) {
                source.close()
                throw SecurityException("Invalid chunked file: IV prefix truncated")
            }
            
            val chunkSizeBytes = ByteArray(4)
            if (source.read(chunkSizeBytes) != 4) {
                source.close()
                throw SecurityException("Invalid chunked file: chunk size missing")
            }
        }
        
        override fun read(): Int {
            if (eof) return -1
            
            // If current chunk is exhausted, load next
            if (currentChunkPlaintext == null || posInChunk >= currentChunkPlaintext!!.size) {
                if (!loadNextChunk()) {
                    eof = true
                    return -1
                }
            }
            
            return currentChunkPlaintext!![posInChunk++].toInt() and 0xFF
        }
        
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (eof) return -1
            if (len == 0) return 0
            
            var totalCopied = 0
            
            while (totalCopied < len) {
                // Load next chunk if needed
                if (currentChunkPlaintext == null || posInChunk >= currentChunkPlaintext!!.size) {
                    // Zero previous chunk before loading next
                    currentChunkPlaintext?.let { java.util.Arrays.fill(it, 0.toByte()) }
                    
                    if (!loadNextChunk()) {
                        eof = true
                        return if (totalCopied > 0) totalCopied else -1
                    }
                }
                
                val available = currentChunkPlaintext!!.size - posInChunk
                val toCopy = minOf(available, len - totalCopied)
                System.arraycopy(currentChunkPlaintext!!, posInChunk, b, off + totalCopied, toCopy)
                posInChunk += toCopy
                totalCopied += toCopy
            }
            
            return totalCopied
        }
        
        private fun loadNextChunk(): Boolean {
            // Read chunk length
            val lenBytes = ByteArray(4)
            val lenRead = readFullyFromStream(source, lenBytes)
            if (lenRead <= 0) return false
            if (lenRead != 4) throw SecurityException("Chunk length truncated at chunk $chunkCounter")
            
            val chunkLen = ByteBuffer.wrap(lenBytes).int
            if (chunkLen <= 0 || chunkLen > CHUNK_PLAINTEXT_SIZE + 16 + 256) {
                throw SecurityException("Invalid chunk length: $chunkLen at chunk $chunkCounter")
            }
            
            // Read chunk ciphertext
            val chunkCiphertext = ByteArray(chunkLen)
            val chunkRead = readFullyFromStream(source, chunkCiphertext)
            if (chunkRead != chunkLen) {
                throw SecurityException("Chunk $chunkCounter truncated")
            }
            
            // Derive nonce and decrypt
            val nonce = deriveChunkNonce(ivPrefix, chunkCounter)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            currentChunkPlaintext = try {
                cipher.doFinal(chunkCiphertext)
            } catch (e: Exception) {
                throw SecurityException("Chunk $chunkCounter authentication failed — possible tampering", e)
            }
            
            posInChunk = 0
            chunkCounter++
            
            return true
        }
        
        override fun close() {
            // Zero any remaining plaintext in memory
            currentChunkPlaintext?.let { java.util.Arrays.fill(it, 0.toByte()) }
            currentChunkPlaintext = null
            source.close()
        }
    }
    
    /**
     * OutputStream that encrypts and authenticates one chunk at a time.
     * Each chunk is independently GCM-encrypted with a unique nonce.
     */
    private inner class ChunkedAeadOutputStream(
        private val destination: OutputStream,
        private val key: SecretKey
    ) : OutputStream() {
        
        private val ivPrefix = ByteArray(IV_PREFIX_SIZE)
        private val buffer = ByteArray(CHUNK_PLAINTEXT_SIZE)
        private var bufferPos = 0
        private var chunkCounter = 0
        private var closed = false
        
        init {
            SecureRandom().nextBytes(ivPrefix)
            
            // Write header
            destination.write(MAGIC)
            destination.write(FORMAT_VERSION.toInt())
            destination.write(ivPrefix)
            destination.write(ByteBuffer.allocate(4).putInt(CHUNK_PLAINTEXT_SIZE).array())
        }
        
        override fun write(b: Int) {
            if (closed) throw IllegalStateException("Stream is closed")
            buffer[bufferPos++] = b.toByte()
            if (bufferPos >= CHUNK_PLAINTEXT_SIZE) {
                flushChunk()
            }
        }
        
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IllegalStateException("Stream is closed")
            var remaining = len
            var offset = off
            
            while (remaining > 0) {
                val space = CHUNK_PLAINTEXT_SIZE - bufferPos
                val toCopy = minOf(space, remaining)
                System.arraycopy(b, offset, buffer, bufferPos, toCopy)
                bufferPos += toCopy
                offset += toCopy
                remaining -= toCopy
                
                if (bufferPos >= CHUNK_PLAINTEXT_SIZE) {
                    flushChunk()
                }
            }
        }
        
        private fun flushChunk() {
            if (bufferPos == 0) return
            
            val nonce = deriveChunkNonce(ivPrefix, chunkCounter)
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_SIZE, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            
            val chunkCiphertext = cipher.doFinal(buffer, 0, bufferPos)
            
            // Write: [4-byte length][ciphertext+tag]
            destination.write(ByteBuffer.allocate(4).putInt(chunkCiphertext.size).array())
            destination.write(chunkCiphertext)
            
            // Zero the plaintext buffer
            java.util.Arrays.fill(buffer, 0, bufferPos, 0.toByte())
            bufferPos = 0
            chunkCounter++
            
            if (chunkCounter == Int.MAX_VALUE) {
                throw SecurityException("File too large: chunk counter overflow")
            }
        }
        
        override fun flush() {
            // Don't flush partial chunks — only close() finalizes
            destination.flush()
        }
        
        override fun close() {
            if (!closed) {
                closed = true
                // Flush any remaining data as the final chunk
                flushChunk()
                // Zero the buffer
                java.util.Arrays.fill(buffer, 0.toByte())
                destination.close()
            }
        }
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
