package com.pionen.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.core.vault.VaultFile
import com.pionen.app.core.vault.VaultStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultEngine: VaultEngine
) : ViewModel() {
    
    // distinctUntilChanged prevents unnecessary recomposition when content hasn't changed
    val files: Flow<List<VaultFile>> = vaultEngine.getAllFiles().distinctUntilChanged()
    
    private val _vaultStats = MutableStateFlow<VaultStats?>(null)
    val vaultStats: StateFlow<VaultStats?> = _vaultStats
    
    init {
        loadStats()
    }
    
    fun loadStats() {
        viewModelScope.launch {
            try {
                _vaultStats.value = vaultEngine.getVaultStats()
            } catch (e: Exception) {
                // Database may not be ready yet — tolerate gracefully
                _vaultStats.value = VaultStats(0, 0L, 0L)
            }
        }
    }
    
    fun deleteFile(fileId: UUID) {
        viewModelScope.launch {
            vaultEngine.deleteFile(fileId)
            loadStats()
        }
    }
}
