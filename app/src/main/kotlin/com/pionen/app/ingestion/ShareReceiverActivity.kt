package com.pionen.app.ingestion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pionen.app.core.security.ScreenshotShield
import com.pionen.app.core.vault.VaultEngine
import com.pionen.app.ui.theme.PionenTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ShareReceiverActivity: Handles share intents from other apps.
 * 
 * Security Design:
 * - Displays warning about external file security limitations
 * - Files streamed directly to encrypted vault
 * - Original file NOT modified/deleted (can't guarantee erasure)
 * - User must acknowledge security warning before import
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    
    @Inject
    lateinit var vaultEngine: VaultEngine
    
    @Inject
    lateinit var screenshotShield: ScreenshotShield
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply screenshot protection
        screenshotShield.protect(this)
        
        val uris = extractUris()
        
        if (uris.isEmpty()) {
            Toast.makeText(this, "No files to import", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setContent {
            PionenTheme {
                ShareReceiverScreen(
                    fileCount = uris.size,
                    onConfirm = {
                        lifecycleScope.launch {
                            importFiles(uris)
                        }
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
    
    private fun extractUris(): List<Uri> {
        val uris = mutableListOf<Uri>()
        
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { 
                    uris.addAll(it) 
                }
            }
        }
        
        return uris
    }
    
    private suspend fun importFiles(uris: List<Uri>) {
        var imported = 0
        var failed = 0
        
        for (uri in uris) {
            try {
                vaultEngine.importFile(uri)
                imported++
            } catch (e: Exception) {
                failed++
            }
        }
        
        val message = when {
            failed == 0 -> "Imported $imported file(s) to vault"
            imported == 0 -> "Failed to import files"
            else -> "Imported $imported, failed $failed"
        }
        
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareReceiverScreen(
    fileCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var isImporting by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Warning Icon
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.displaySmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Title
                    Text(
                        text = "Security Warning",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Warning Message
                    Text(
                        text = buildString {
                            append("Importing $fileCount file(s)\n\n")
                            append("These files existed unencrypted on your device. ")
                            append("While they will be encrypted in Pionen's vault, ")
                            append("the original plaintext may still be recoverable from your device's storage ")
                            append("due to flash wear-leveling.\n\n")
                            append("For maximum security, only create files directly within Pionen.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            enabled = !isImporting
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                isImporting = true
                                onConfirm()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isImporting
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("I Understand, Import")
                            }
                        }
                    }
                }
            }
        }
    }
}
