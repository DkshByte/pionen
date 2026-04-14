package com.pionen.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.vault.DecryptedContent
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.media.SecureMediaPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    private val vaultEngine: VaultEngine,
    val mediaPlayer: SecureMediaPlayer
) : ViewModel() {
    
    private val _decryptedContent = MutableStateFlow<DecryptedContent?>(null)
    val decryptedContent: StateFlow<DecryptedContent?> = _decryptedContent
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    fun loadFile(fileId: UUID) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val content = vaultEngine.openFile(fileId)
                _decryptedContent.value = content
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to decrypt file"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun cleanup() {
        // Securely wipe the buffer when leaving the screen
        _decryptedContent.value?.buffer?.close()
        _decryptedContent.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
