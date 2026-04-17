package com.pionen.app.core.crypto

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.util.Arrays

/**
 * SecureBuffer: A RAM-only buffer that securely wipes memory on close.
 * 
 * Security Design:
 * - Decrypted content only exists in RAM, never on disk
 * - Content is zeroed when buffer is closed
 * - Implements Closeable for use with try-with-resources / use { }
 */
class SecureBuffer private constructor(
    private val data: ByteArray
) : Closeable {
    
    @Volatile
    private var isClosed = false
    
    val size: Int get() = if (isClosed) 0 else data.size
    
    companion object {
        /**
         * Create a SecureBuffer with the given data.
         * The data array is owned by the buffer and will be zeroed on close.
         */
        fun wrap(data: ByteArray): SecureBuffer {
            return SecureBuffer(data)
        }
        
        /**
         * Allocate an empty buffer of the given size.
         */
        fun allocate(size: Int): SecureBuffer {
            return SecureBuffer(ByteArray(size))
        }
    }
    
    /**
     * Get a copy of the underlying data.
     * Throws if buffer is closed.
     * The returned copy is NOT zeroed on close — caller is responsible.
     */
    fun getData(): ByteArray {
        check(!isClosed) { "SecureBuffer has been closed" }
        return data.copyOf()
    }
    
    /**
     * Get a DIRECT reference to the underlying data (zero-copy).
     * WARNING: The returned array WILL be zeroed when close() is called.
     * Only use this when you need the reference to stay in sync with the buffer
     * lifecycle (e.g. ExoPlayer DataSource) and will NOT access it after close.
     */
    fun getDataDirect(): ByteArray {
        check(!isClosed) { "SecureBuffer has been closed" }
        return data
    }
    
    /**
     * Get an InputStream for reading the buffer content.
     * Do NOT cache this stream - it may become invalid after close.
     */
    fun asInputStream(): InputStream {
        check(!isClosed) { "SecureBuffer has been closed" }
        return ByteArrayInputStream(data)
    }
    
    /**
     * Copy data out of the buffer.
     * Use sparingly - prefer streaming operations.
     */
    fun copyData(): ByteArray {
        check(!isClosed) { "SecureBuffer has been closed" }
        return data.copyOf()
    }
    
    /**
     * Securely wipe and close the buffer.
     * After close, the buffer data is zeroed and cannot be accessed.
     */
    override fun close() {
        if (!isClosed) {
            isClosed = true
            // Securely wipe the buffer content
            Arrays.fill(data, 0.toByte())
        }
    }
    
    /**
     * Ensure buffer is closed if forgotten.
     */
    protected fun finalize() {
        close()
    }
}

