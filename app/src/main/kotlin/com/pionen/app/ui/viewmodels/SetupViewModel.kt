package com.pionen.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val lockManager: LockManager
) : ViewModel() {

    /**
     * Initializes the Vault with a new PIN.
     * Uses the Hardware HMAC PBKDF2 hashing standard defined in LockManager.
     */
    fun setupPin(pin: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                lockManager.setPin(pin)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}
