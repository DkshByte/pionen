package com.pionen.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pionen.app.server.SecureWebServer
import com.pionen.app.ui.theme.*

/**
 * Premium dialog for web access with QR code and server controls.
 */
@Composable
fun WebAccessDialog(
    serverState: SecureWebServer.ServerState,
    serverInfo: SecureWebServer.ServerInfo?,
    qrCodeBitmap: Bitmap?,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedField by remember { mutableStateOf<String?>(null) }
    
    // Copy feedback animation
    LaunchedEffect(copiedField) {
        if (copiedField != null) {
            kotlinx.coroutines.delay(2000)
            copiedField = null
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(
                    elevation = 32.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = VaultGreen.copy(alpha = 0.1f),
                    spotColor = VaultGreen.copy(alpha = 0.2f)
                ),
            shape = RoundedCornerShape(24.dp),
            color = DarkCard,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassBorder,
                        GlassBorder.copy(alpha = 0.3f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Status indicator
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = when (serverState) {
                                        is SecureWebServer.ServerState.Running -> VaultGreen
                                        is SecureWebServer.ServerState.Starting -> Color(0xFFFBBF24)
                                        is SecureWebServer.ServerState.Error -> Color(0xFFF87171)
                                        else -> TextMuted
                                    },
                                    shape = CircleShape
                                )
                        )
                        
                        Text(
                            text = "Web Access",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Content based on state
                when (serverState) {
                    is SecureWebServer.ServerState.Stopped -> {
                        StoppedContent(onStart = onStartServer)
                    }
                    is SecureWebServer.ServerState.Starting -> {
                        StartingContent()
                    }
                    is SecureWebServer.ServerState.Running -> {
                        RunningContent(
                            serverInfo = serverInfo!!,
                            qrCodeBitmap = qrCodeBitmap,
                            copiedField = copiedField,
                            onCopyUrl = {
                                clipboardManager.setText(AnnotatedString(serverInfo.url))
                                copiedField = "url"
                            },
                            onCopyToken = {
                                clipboardManager.setText(AnnotatedString(serverInfo.token))
                                copiedField = "token"
                            },
                            onStop = onStopServer
                        )
                    }
                    is SecureWebServer.ServerState.Error -> {
                        ErrorContent(
                            message = serverState.message,
                            onRetry = onStartServer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoppedContent(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon
        Surface(
            shape = CircleShape,
            color = VaultGreen.copy(alpha = 0.1f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = VaultGreen,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Text(
            text = "Access Vault from Computer",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "Start a secure server to browse and download your files from any device on the same network.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Security badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecurityBadge(icon = Icons.Default.Lock, text = "Encrypted")
            SecurityBadge(icon = Icons.Default.VpnKey, text = "Token Auth")
            SecurityBadge(icon = Icons.Default.Router, text = "LAN Only")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Start button
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultGreen,
                contentColor = Color.Black
            )
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Start Server",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun StartingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 40.dp)
    ) {
        CircularProgressIndicator(
            color = VaultGreen,
            strokeWidth = 3.dp
        )
        
        Text(
            text = "Starting secure server...",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
    }
}

@Composable
private fun RunningContent(
    serverInfo: SecureWebServer.ServerInfo,
    qrCodeBitmap: Bitmap?,
    copiedField: String?,
    onCopyUrl: () -> Unit,
    onCopyToken: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // QR Code
        qrCodeBitmap?.let { bitmap ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.size(200.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code for server access",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }
        
        Text(
            text = "Scan with your computer or phone camera",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        
        Divider(
            color = GlassBorder,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // URL Field
        CopyableField(
            label = "URL",
            value = serverInfo.url,
            isCopied = copiedField == "url",
            onCopy = onCopyUrl
        )
        
        // Token Field
        CopyableField(
            label = "Access Token",
            value = serverInfo.token,
            isCopied = copiedField == "token",
            onCopy = onCopyToken,
            isMonospace = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Auto-timeout info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Auto-stops after 10 minutes of inactivity",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Stop button
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFF87171)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFFF87171).copy(alpha = 0.5f)
            )
        ) {
            Icon(
                Icons.Default.Stop,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Stop Server",
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFF87171).copy(alpha = 0.1f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Text(
            text = "Failed to start server",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultGreen
            )
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun SecurityBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VaultGreen.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            VaultGreen.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = VaultGreen,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = VaultGreen
            )
        }
    }
}

@Composable
private fun CopyableField(
    label: String,
    value: String,
    isCopied: Boolean,
    onCopy: () -> Unit,
    isMonospace: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onCopy)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                    maxLines = 1
                )
            }
            
            AnimatedContent(
                targetState = isCopied,
                label = "copyIcon"
            ) { copied ->
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (copied) VaultGreen else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
