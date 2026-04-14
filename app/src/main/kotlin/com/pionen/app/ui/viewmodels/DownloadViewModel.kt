package com.pionen.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pionen.app.ingestion.DownloadProgress
import com.pionen.app.ingestion.SecureDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val secureDownloader: SecureDownloader
) : ViewModel() {
    
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress
    
    fun downloadToVault(url: String) {
        viewModelScope.launch {
            secureDownloader.downloadToVault(url).collect { progress ->
                _downloadProgress.value = progress
            }
        }
    }
}
