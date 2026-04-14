package com.pionen.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.vault.DecryptedContent
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.core.vault.VaultFile
import com.pionen.app.media.SecureMediaPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for gallery-style file viewing with swipe navigation.
 * Manages file list and current decrypted content.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val vaultEngine: VaultEngine,
    val mediaPlayer: SecureMediaPlayer
) : ViewModel() {
    
    val files = vaultEngine.getAllFiles()
    
    private val _currentContent = MutableStateFlow<DecryptedContent?>(null)
    val currentContent: StateFlow<DecryptedContent?> = _currentContent
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private var currentFileId: UUID? = null
    
    fun loadFile(fileId: UUID) {
        // Don't reload if same file
        if (fileId == currentFileId && _currentContent.value != null) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Cleanup previous content
            _currentContent.value?.buffer?.close()
            
            try {
                val content = vaultEngine.openFile(fileId)
                currentFileId = fileId
                _currentContent.value = content
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to decrypt file"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun cleanup() {
        _currentContent.value?.buffer?.close()
        _currentContent.value = null
        currentFileId = null
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
