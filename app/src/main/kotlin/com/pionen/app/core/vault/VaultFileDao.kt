package com.pionen.app.core.vault

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for VaultFile operations.
 * Database is encrypted with SQLCipher.
 */
@Dao
interface VaultFileDao {
    
    @Query("SELECT * FROM vault_files ORDER BY created_at DESC")
    fun getAllFiles(): Flow<List<VaultFile>>
    
    @Query("SELECT * FROM vault_files WHERE id = :fileId")
    suspend fun getFile(fileId: UUID): VaultFile?
    
    @Query("SELECT * FROM vault_files WHERE file_name LIKE '%' || :query || '%'")
    fun searchFiles(query: String): Flow<List<VaultFile>>
    
    @Query("SELECT * FROM vault_files WHERE mime_type LIKE :mimeTypePrefix || '%'")
    fun getFilesByType(mimeTypePrefix: String): Flow<List<VaultFile>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: VaultFile)
    
    @Update
    suspend fun update(file: VaultFile)
    
    @Delete
    suspend fun delete(file: VaultFile)
    
    @Query("DELETE FROM vault_files WHERE id = :fileId")
    suspend fun deleteById(fileId: UUID)
    
    @Query("DELETE FROM vault_files")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM vault_files")
    suspend fun getFileCount(): Int
    
    @Query("SELECT SUM(original_size) FROM vault_files")
    suspend fun getTotalOriginalSize(): Long?
    
    @Query("SELECT * FROM vault_files ORDER BY created_at DESC LIMIT :limit")
    fun getRecentFiles(limit: Int): Flow<List<VaultFile>>
}
