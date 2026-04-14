package com.pionen.app.ui.screens

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.vault.DecryptedContent
import com.pionen.app.core.vault.FileType
import com.pionen.app.ui.theme.VaultGreen
import com.pionen.app.ui.viewmodels.FileViewerViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * File viewer screen with RAM-only decryption.
 * Content is decrypted and displayed without disk caching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    fileId: String,
    onBack: () -> Unit,
    viewModel: FileViewerViewModel = hiltViewModel()
) {
    val decryptedContent by viewModel.decryptedContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    LaunchedEffect(fileId) {
        viewModel.loadFile(UUID.fromString(fileId))
    }
    
    // Clean up on exit
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = decryptedContent?.file?.fileName ?: "Loading...",
                            maxLines = 1
                        )
                        decryptedContent?.file?.let { file ->
                            Text(
                                text = file.formattedSize,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Decrypting...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Failed to decrypt file",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                decryptedContent != null -> {
                    FileContentViewer(
                        content = decryptedContent!!,
                        mediaPlayer = viewModel.mediaPlayer
                    )
                }
            }
        }
    }
}

@Composable
private fun FileContentViewer(
    content: DecryptedContent,
    mediaPlayer: com.pionen.app.media.SecureMediaPlayer
) {
    val fileType = FileType.fromMimeType(content.file.mimeType)
    
    when (fileType) {
        FileType.IMAGE -> ImageViewer(content)
        FileType.TEXT -> TextViewer(content)
        FileType.VIDEO -> VideoPlayerView(content, mediaPlayer)
        FileType.AUDIO -> AudioPlayerView(content, mediaPlayer)
        FileType.PDF -> PdfPreview(content)
        FileType.DOCUMENT -> DocumentPreview(content)
        FileType.UNKNOWN -> GenericFilePreview(content)
    }
}

@Composable
private fun ImageViewer(content: DecryptedContent) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    val bitmap = remember(content) {
        try {
            val data = content.buffer.getData()
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = content.file.fileName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        )
    } else {
        Text(
            text = "Could not decode image",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun TextViewer(content: DecryptedContent) {
    val text = remember(content) {
        try {
            String(content.buffer.getData())
        } catch (e: Exception) {
            "Could not decode text"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun VideoPreview(content: DecryptedContent) {
    FilePreviewCard(
        icon = Icons.Default.VideoFile,
        fileType = "Video",
        content = content,
        details = listOf(
            "Type" to content.file.mimeType,
            "Size" to content.file.formattedSize,
            "Created" to formatDate(content.file.createdAt)
        ),
        message = "Video playback from encrypted storage requires temporary decryption. Preview not available for security."
    )
}

@Composable
private fun AudioPreview(content: DecryptedContent) {
    FilePreviewCard(
        icon = Icons.Default.AudioFile,
        fileType = "Audio",
        content = content,
        details = listOf(
            "Type" to content.file.mimeType,
            "Size" to content.file.formattedSize,
            "Created" to formatDate(content.file.createdAt)
        ),
        message = "Audio playback from encrypted storage requires temporary decryption."
    )
}

@Composable
private fun PdfPreview(content: DecryptedContent) {
    FilePreviewCard(
        icon = Icons.Default.PictureAsPdf,
        fileType = "PDF Document",
        content = content,
        details = listOf(
            "Size" to content.file.formattedSize,
            "Pages" to "Unknown",
            "Created" to formatDate(content.file.createdAt)
        ),
        message = "PDF preview would require temporary decryption to render pages."
    )
}

@Composable
private fun DocumentPreview(content: DecryptedContent) {
    val extension = content.file.fileName.substringAfterLast('.', "").uppercase()
    val docType = when (extension) {
        "DOC", "DOCX" -> "Word Document"
        "XLS", "XLSX" -> "Excel Spreadsheet"
        "PPT", "PPTX" -> "PowerPoint Presentation"
        "ZIP", "RAR", "7Z" -> "Archive"
        "APK" -> "Android App"
        else -> "Document"
    }
    
    FilePreviewCard(
        icon = getDocumentIcon(extension),
        fileType = docType,
        content = content,
        details = listOf(
            "Format" to extension,
            "Size" to content.file.formattedSize,
            "Created" to formatDate(content.file.createdAt)
        ),
        message = "Document is encrypted and secure."
    )
}

@Composable
private fun GenericFilePreview(content: DecryptedContent) {
    val extension = content.file.fileName.substringAfterLast('.', "").uppercase()
    
    FilePreviewCard(
        icon = Icons.Default.InsertDriveFile,
        fileType = if (extension.isNotEmpty()) "$extension File" else "Unknown File",
        content = content,
        details = listOf(
            "MIME Type" to content.file.mimeType,
            "Size" to content.file.formattedSize,
            "Created" to formatDate(content.file.createdAt),
            "Encrypted" to "Yes (AES-256-GCM)"
        ),
        message = "This file type cannot be previewed in-app."
    )
}

@Composable
private fun FilePreviewCard(
    icon: ImageVector,
    fileType: String,
    content: DecryptedContent,
    details: List<Pair<String, String>>,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Large icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = VaultGreen
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // File name
        Text(
            text = content.file.fileName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = fileType,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                details.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Security indicator
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(VaultGreen.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = VaultGreen
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Encrypted with AES-256-GCM",
                style = MaterialTheme.typography.labelMedium,
                color = VaultGreen
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Message
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun getDocumentIcon(extension: String): ImageVector {
    return when (extension.uppercase()) {
        "DOC", "DOCX" -> Icons.Default.Article
        "XLS", "XLSX" -> Icons.Default.TableChart
        "PPT", "PPTX" -> Icons.Default.Slideshow
        "ZIP", "RAR", "7Z" -> Icons.Default.FolderZip
        "APK" -> Icons.Default.Android
        else -> Icons.Default.Description
    }
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}
