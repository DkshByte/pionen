package com.pionen.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.core.security.PanicManager
import com.pionen.app.core.security.PanicState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PanicViewModel @Inject constructor(
    private val panicManager: PanicManager
) : ViewModel() {
    
    val panicState: StateFlow<PanicState> = panicManager.panicState
    
    fun executePanicWipe() {
        viewModelScope.launch {
            panicManager.executePanicWipe()
        }
    }
    
    fun cancel() {
        panicManager.cancelPanicWipe()
    }
}
