package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.ingestion.DownloadProgress
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.DownloadViewModel

/**
 * Premium download screen with elegant progress animations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    var url by remember { mutableStateOf("") }
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    
    // Screen entrance
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Secure Download",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { padding ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(400)) + slideInVertically(
                animationSpec = tween(400, easing = PionenEasing.EaseOut),
                initialOffsetY = { it / 20 }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Security notice card
                Card(
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = NeonGlassSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGlassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.ui.graphics.RectangleShape)
                                .background(NeonGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "End-to-End Encrypted",
                                style = MaterialTheme.typography.titleSmall,
                                color = NeonGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Files are downloaded over TLS and encrypted directly to the vault. No plaintext copy is stored.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // URL input
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Enter URL", color = TextSecondary) },
                    placeholder = { Text("https://example.com/file.pdf", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null, tint = TextMuted)
                    },
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (url.isNotBlank()) {
                                viewModel.downloadToVault(url)
                            }
                        }
                    ),
                    enabled = downloadProgress !is DownloadProgress.Downloading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = GlassBorder.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ElectricCyan
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Download button
                Button(
                    onClick = { viewModel.downloadToVault(url) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = url.isNotBlank() && downloadProgress !is DownloadProgress.Downloading,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreenDark,
                        disabledContainerColor = NeonGreenDark.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download to Vault", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Progress indicator with animations
                AnimatedContent(
                    targetState = downloadProgress,
                    transitionSpec = {
                        fadeIn(tween(300)) + slideInVertically(
                            animationSpec = tween(300),
                            initialOffsetY = { it / 4 }
                        ) togetherWith fadeOut(tween(200))
                    },
                    label = "progress"
                ) { progress ->
                    when (progress) {
                        is DownloadProgress.Starting -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.ui.graphics.RectangleShape)
                                    .background(DarkCard)
                                    .padding(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Starting download...",
                                    color = TextPrimary
                                )
                            }
                        }
                        is DownloadProgress.Downloading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.ui.graphics.RectangleShape)
                                    .background(DarkCard)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Downloading...", color = TextPrimary)
                                    if (progress.percentComplete >= 0) {
                                        Text(
                                            "${progress.percentComplete.toInt()}%",
                                            color = NeonGreen
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Animated progress bar
                                val animatedProgress by animateFloatAsState(
                                    targetValue = if (progress.percentComplete >= 0) progress.percentComplete / 100f else 0f,
                                    animationSpec = tween(300),
                                    label = "progressBar"
                                )
                                
                                if (progress.percentComplete >= 0) {
                                    LinearProgressIndicator(
                                        progress = animatedProgress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(androidx.compose.ui.graphics.RectangleShape),
                                        color = NeonGreen,
                                        trackColor = DarkSurfaceVariant
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = NeonGreen,
                                        trackColor = DarkSurfaceVariant
                                    )
                                }
                            }
                        }
                        is DownloadProgress.Complete -> {
                            val scale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.4f),
                                label = "successScale"
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(scale)
                                    .clip(androidx.compose.ui.graphics.RectangleShape)
                                    .background(NeonGlassSurface)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(androidx.compose.ui.graphics.RectangleShape)
                                        .background(NeonGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonGreen
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Downloaded successfully!",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NeonGreen
                                    )
                                    Text(
                                        text = progress.file.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        is DownloadProgress.Failed -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.ui.graphics.RectangleShape)
                                    .background(DestructiveRed.copy(alpha = 0.1f))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(androidx.compose.ui.graphics.RectangleShape)
                                        .background(DestructiveRed.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = DestructiveRed
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Download failed",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = DestructiveRed
                                    )
                                    Text(
                                        text = progress.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        null -> {}
                    }
                }
            }
        }
    }
}
