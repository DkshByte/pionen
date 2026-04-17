package com.pionen.app.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.crypto.KeyManager
import com.pionen.app.core.network.TorConnectionState
import com.pionen.app.core.network.TorManager
import com.pionen.app.core.network.TorService
import com.pionen.app.core.network.VpnStatus
import com.pionen.app.core.network.VpnStatusManager
import com.pionen.app.core.security.DecoyVaultManager
import com.pionen.app.core.security.IntruderCapture
import com.pionen.app.core.security.IntruderCaptureManager
import com.pionen.app.core.security.LockManager
import com.pionen.app.core.security.StealthManager
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.core.vault.VaultStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultEngine: VaultEngine,
    private val keyManager: KeyManager,
    private val stealthManager: StealthManager,
    private val lockManager: LockManager,
    private val decoyVaultManager: DecoyVaultManager,
    private val intruderCaptureManager: IntruderCaptureManager,
    private val torManager: TorManager,
    private val vpnStatusManager: VpnStatusManager
) : ViewModel() {
    
    private val _vaultStats = MutableStateFlow<VaultStats?>(null)
    val vaultStats: StateFlow<VaultStats?> = _vaultStats
    
    private val _isHardwareBacked = MutableStateFlow(false)
    val isHardwareBacked: StateFlow<Boolean> = _isHardwareBacked
    
    private val _isStrongBoxBacked = MutableStateFlow(false)
    val isStrongBoxBacked: StateFlow<Boolean> = _isStrongBoxBacked
    
    val isPinConfigured: StateFlow<Boolean> = lockManager.isPinConfigured
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    val currentDisguise: StateFlow<StealthManager.Disguise> = stealthManager.currentDisguise
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StealthManager.Disguise.DEFAULT
        )
    
    // Decoy vault state
    val isDecoyEnabled: StateFlow<Boolean> = decoyVaultManager.isDecoyEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    // Intruder capture state
    val isIntruderCaptureEnabled: StateFlow<Boolean> = intruderCaptureManager.isCaptureEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    private val _intruderCaptures = MutableStateFlow<List<IntruderCapture>>(emptyList())
    val intruderCaptures: StateFlow<List<IntruderCapture>> = _intruderCaptures
    
    private val _decoyAccessCount = MutableStateFlow(0)
    val decoyAccessCount: StateFlow<Int> = _decoyAccessCount
    
    // ===== TOR & VPN STATE =====
    
    val torConnectionState: StateFlow<TorConnectionState> = torManager.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TorConnectionState.Disconnected
        )
    
    val torBootstrapProgress: StateFlow<Int> = torManager.bootstrapProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    val isTorEnabled: StateFlow<Boolean> = torManager.isEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    val vpnStatus: StateFlow<VpnStatus> = vpnStatusManager.vpnStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VpnStatus.Unknown
        )
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            _vaultStats.value = vaultEngine.getVaultStats()
            
            val fileIds = keyManager.getAllFileIds()
            if (fileIds.isNotEmpty()) {
                val info = keyManager.getKeyProtectionInfo(fileIds.first())
                _isHardwareBacked.value = info.isHardwareBacked
                _isStrongBoxBacked.value = info.isStrongBoxBacked
            }
            
            // Load security audit data
            _intruderCaptures.value = intruderCaptureManager.getIntruderCaptures()
            _decoyAccessCount.value = decoyVaultManager.getDecoyAccessCount()
        }
    }
    
    suspend fun switchDisguise(disguise: StealthManager.Disguise) {
        stealthManager.switchDisguise(disguise)
    }
    
    suspend fun setPin(pin: String) {
        lockManager.setPin(pin)
    }
    
    // ===== DECOY VAULT =====
    
    /**
     * Enable decoy vault. Returns false if the decoy PIN matches the real PIN.
     */
    suspend fun enableDecoyVault(decoyPin: String): Boolean {
        return try {
            decoyVaultManager.enableDecoyVault(decoyPin)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
    
    suspend fun disableDecoyVault() {
        decoyVaultManager.disableDecoyVault()
    }
    
    // ===== INTRUDER CAPTURE =====
    
    suspend fun enableIntruderCapture(threshold: Int = 2) {
        intruderCaptureManager.enableCapture(threshold)
    }
    
    suspend fun disableIntruderCapture() {
        intruderCaptureManager.disableCapture()
    }
    
    fun refreshIntruderCaptures() {
        viewModelScope.launch {
            _intruderCaptures.value = intruderCaptureManager.getIntruderCaptures()
        }
    }
    
    suspend fun clearAllIntruderCaptures() {
        intruderCaptureManager.clearAllCaptures()
        _intruderCaptures.value = emptyList()
    }
    
    fun deleteIntruderCapture(capture: IntruderCapture) {
        intruderCaptureManager.deleteCapture(capture)
        _intruderCaptures.value = _intruderCaptures.value.filter { it != capture }
    }
    
    // ===== TOR CONTROLS =====
    
    fun toggleTor() {
        if (torManager.isEnabled.value) {
            TorService.stopService(context)
            torManager.stopTor()
        } else {
            TorService.startService(context)
        }
    }
    
    // ===== VPN STATUS =====
    
    fun refreshVpnStatus() {
        vpnStatusManager.refresh()
    }
}
