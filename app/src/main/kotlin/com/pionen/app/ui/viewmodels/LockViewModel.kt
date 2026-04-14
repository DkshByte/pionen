package com.pionen.app.ui.viewmodels

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.security.DecoyVaultManager
import com.pionen.app.core.security.IntruderCaptureManager
import com.pionen.app.core.security.LockManager
import com.pionen.app.core.security.LockState
import com.pionen.app.core.security.UnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockManager: LockManager,
    private val decoyVaultManager: DecoyVaultManager,
    private val intruderCaptureManager: IntruderCaptureManager
) : ViewModel() {
    
    val lockState: StateFlow<LockState> = lockManager.lockState
    val failedAttempts: StateFlow<Int> = lockManager.failedAttempts
    val biometricPassed: StateFlow<Boolean> = lockManager.biometricPassed
    
    val isPinConfigured: StateFlow<Boolean> = lockManager.isPinConfigured
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )
    
    // Track if we're in decoy mode (showing fake vault)
    private val _isDecoyMode = MutableStateFlow(false)
    val isDecoyMode: StateFlow<Boolean> = _isDecoyMode.asStateFlow()
    
    fun isBiometricAvailable(): Boolean {
        return lockManager.isBiometricAvailable()
    }
    
    suspend fun authenticateBiometric(activity: FragmentActivity): UnlockResult {
        val result = lockManager.authenticateBiometric(activity)
        
        // Trigger intruder capture on failed biometric
        if (result is UnlockResult.Error || result is UnlockResult.TooManyAttempts) {
            viewModelScope.launch {
                intruderCaptureManager.onFailedAttempt(failedAttempts.value)
            }
        }
        
        return result
    }
    
    suspend fun verifyPin(pin: String): Boolean {
        // First check if this is a decoy PIN
        val isDecoy = decoyVaultManager.isDecoyPin(pin)
        if (isDecoy) {
            // Enter decoy mode - user sees fake vault
            _isDecoyMode.value = true
            // Simulate successful unlock (but we're in decoy mode)
            return true
        }
        
        // Try real PIN
        val success = lockManager.verifyPin(pin)
        
        if (!success) {
            // Trigger intruder capture on failed PIN
            viewModelScope.launch {
                intruderCaptureManager.onFailedAttempt(failedAttempts.value)
            }
        } else {
            // Real unlock - ensure not in decoy mode
            _isDecoyMode.value = false
        }
        
        return success
    }
    
    fun lock() {
        lockManager.lock()
        _isDecoyMode.value = false
    }
}
