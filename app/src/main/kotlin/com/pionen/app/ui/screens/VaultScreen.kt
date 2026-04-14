package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.vault.FileType
import com.pionen.app.core.vault.VaultFile
import com.pionen.app.ui.components.FileTypeFilter
import com.pionen.app.ui.components.FloatingActionBar
import com.pionen.app.ui.components.FloatingSettingsButton
import com.pionen.app.ui.components.SortOption
import com.pionen.app.ui.components.WebAccessDialog
import com.pionen.app.server.SecureWebServer
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.VaultViewModel
import com.pionen.app.ui.viewmodels.WebServerViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Premium vault screen with floating action bar and refined file cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onFileClick: (UUID) -> Unit,
    onGalleryClick: (Int) -> Unit,
    onCameraClick: () -> Unit,
    onBrowserClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    webServerViewModel: WebServerViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState(initial = emptyList())
    val vaultStats by viewModel.vaultStats.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<VaultFile?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    
    // Web server state
    val serverState by webServerViewModel.serverState.collectAsState()
    val serverInfo by webServerViewModel.serverInfo.collectAsState()
    val qrCodeBitmap by webServerViewModel.qrCodeBitmap.collectAsState()
    val showWebAccessDialog by webServerViewModel.showDialog.collectAsState()
    
    // Floating bar state
    var selectedFilter by remember { mutableStateOf(FileTypeFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    
    // Filter and sort the files
    val filteredFiles = remember(files, selectedFilter, searchQuery, sortOption) {
        files
            .filter { file ->
                // Filter by type
                when (selectedFilter) {
                    FileTypeFilter.ALL -> true
                    FileTypeFilter.IMAGES -> file.mimeType.startsWith("image/")
                    FileTypeFilter.VIDEOS -> file.mimeType.startsWith("video/")
                    FileTypeFilter.AUDIO -> file.mimeType.startsWith("audio/")
                    FileTypeFilter.DOCUMENTS -> file.mimeType.startsWith("application/") || 
                            file.mimeType.startsWith("text/")
                }
            }
            .filter { file ->
                // Filter by search query
                searchQuery.isEmpty() || 
                file.fileName.contains(searchQuery, ignoreCase = true)
            }
            .let { filtered ->
                // Sort
                when (sortOption) {
                    SortOption.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
                    SortOption.DATE_ASC -> filtered.sortedBy { it.createdAt }
                    SortOption.NAME_ASC -> filtered.sortedBy { it.fileName.lowercase() }
                    SortOption.NAME_DESC -> filtered.sortedByDescending { it.fileName.lowercase() }
                    SortOption.SIZE_DESC -> filtered.sortedByDescending { it.originalSize }
                    SortOption.SIZE_ASC -> filtered.sortedBy { it.originalSize }
                }
            }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 140.dp)  // Space for floating bar at bottom
        ) {
            
            // Vault stats header
            vaultStats?.let { stats ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Pionen Vault",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "${stats.fileCount} files • ${formatSize(stats.totalOriginalSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    
                    // Active filter indicator
                    if (selectedFilter != FileTypeFilter.ALL || searchQuery.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VaultGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = VaultGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "${filteredFiles.size} results",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultGreen
                                )
                            }
                        }
                    }
                }
            }
            
            // File content
            Box(modifier = Modifier.weight(1f)) {
                if (filteredFiles.isEmpty()) {
                    if (files.isEmpty()) {
                        EmptyVaultState()
                    } else {
                        // No files match filter
                        NoResultsState(onClearFilter = {
                            selectedFilter = FileTypeFilter.ALL
                            searchQuery = ""
                        })
                    }
                } else if (isGridView) {
                    // Grid view
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = filteredFiles,
                            key = { _, file -> file.id }
                        ) { index, file ->
                            var isVisible by remember(file.id) { mutableStateOf(false) }
                            LaunchedEffect(file.id) {
                                isVisible = true
                            }
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(animationSpec = tween(
                                    durationMillis = PionenAnimations.NORMAL,
                                    delayMillis = staggeredDelay(index, PionenAnimations.STAGGER_DELAY)
                                )) +
                                        slideInVertically(
                                            animationSpec = tween(
                                                durationMillis = PionenAnimations.NORMAL,
                                                delayMillis = staggeredDelay(index, PionenAnimations.STAGGER_DELAY),
                                                easing = PionenEasing.EaseOut
                                            ),
                                            initialOffsetY = { it / 4 }
                                        ) +
                                        scaleIn(
                                            animationSpec = tween(
                                                durationMillis = PionenAnimations.NORMAL,
                                                delayMillis = staggeredDelay(index, PionenAnimations.STAGGER_DELAY)
                                            ),
                                            initialScale = 0.9f
                                        )
                            ) {
                                val fileType = FileType.fromMimeType(file.mimeType)
                                val isViewable = fileType == FileType.IMAGE || fileType == FileType.VIDEO || fileType == FileType.TEXT
                                PremiumFileCard(
                                    file = file,
                                    onClick = {
                                        if (isViewable) {
                                            // Find index in viewable files for gallery
                                            val viewableFiles = filteredFiles.filter { f ->
                                                val t = FileType.fromMimeType(f.mimeType)
                                                t == FileType.IMAGE || t == FileType.VIDEO || t == FileType.TEXT
                                            }
                                            val galleryIndex = viewableFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                                            onGalleryClick(galleryIndex)
                                        } else {
                                            onFileClick(file.id)
                                        }
                                    },
                                    onDelete = { showDeleteDialog = file }
                                )
                            }
                        }
                    }
                } else {
                    // List view
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lazyItemsIndexed(
                            items = filteredFiles,
                            key = { _, file -> file.id }
                        ) { index, file ->
                            var isVisible by remember(file.id) { mutableStateOf(false) }
                            LaunchedEffect(file.id) {
                                isVisible = true
                            }
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(animationSpec = tween(
                                    durationMillis = PionenAnimations.NORMAL,
                                    delayMillis = staggeredDelay(index, PionenAnimations.STAGGER_DELAY)
                                )) + slideInHorizontally(
                                    animationSpec = tween(
                                        durationMillis = PionenAnimations.NORMAL,
                                        delayMillis = staggeredDelay(index, PionenAnimations.STAGGER_DELAY),
                                        easing = PionenEasing.EaseOut
                                    ),
                                    initialOffsetX = { -it / 4 }
                                )
                            ) {
                                val fileType = FileType.fromMimeType(file.mimeType)
                                val isViewable = fileType == FileType.IMAGE || fileType == FileType.VIDEO || fileType == FileType.TEXT
                                ListFileCard(
                                    file = file,
                                    onClick = {
                                        if (isViewable) {
                                            val viewableFiles = filteredFiles.filter { f ->
                                                val t = FileType.fromMimeType(f.mimeType)
                                                t == FileType.IMAGE || t == FileType.VIDEO || t == FileType.TEXT
                                            }
                                            val galleryIndex = viewableFiles.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                                            onGalleryClick(galleryIndex)
                                        } else {
                                            onFileClick(file.id)
                                        }
                                    },
                                    onDelete = { showDeleteDialog = file }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Floating Action Bar at bottom
        FloatingActionBar(
            files = files,
            selectedFilter = selectedFilter,
            onFilterChange = { selectedFilter = it },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onRecentFileClick = { fileId -> onFileClick(fileId) },
            onDownloadFromWeb = onBrowserClick,
            onUrlDownload = onDownloadClick,
            onCameraClick = onCameraClick,
            onWebAccessClick = { webServerViewModel.showDialog() },
            onSettingsClick = onSettingsClick,
            isGridView = isGridView,
            onViewToggle = { isGridView = !isGridView },
            sortOption = sortOption,
            onSortChange = { sortOption = it },
            isServerRunning = serverState is SecureWebServer.ServerState.Running,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    
    // Web Access Dialog
    if (showWebAccessDialog) {
        WebAccessDialog(
            serverState = serverState,
            serverInfo = serverInfo,
            qrCodeBitmap = qrCodeBitmap,
            onStartServer = { webServerViewModel.startServer() },
            onStopServer = { webServerViewModel.stopServer() },
            onDismiss = { webServerViewModel.hideDialog() }
        )
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { file ->
        PremiumDeleteDialog(
            file = file,
            onConfirm = {
                viewModel.deleteFile(file.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

@Composable
private fun EmptyVaultState() {
    // Pulsing animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = PionenEasing.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(iconScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkCard,
                            DarkSurfaceVariant
                        )
                    )
                )
                .border(1.dp, GlassBorder.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = TextMuted
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Your vault is empty",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Capture photos or download files securely",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
private fun NoResultsState(
    onClearFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(DarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = TextMuted
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No files found",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Try adjusting your filters or search",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onClearFilter) {
            Icon(
                Icons.Default.FilterListOff,
                contentDescription = null,
                tint = VaultGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear filters", color = VaultGreen)
        }
    }
}

@Composable
private fun PremiumFabCluster(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onCameraClick: () -> Unit,
    onBrowserClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Secondary FABs with staggered animation
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + slideInVertically(
                animationSpec = tween(250, easing = PionenEasing.EaseOut),
                initialOffsetY = { it }
            ),
            exit = fadeOut(tween(150)) + slideOutVertically(
                animationSpec = tween(150),
                targetOffsetY = { it }
            )
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download FAB
                SmallFloatingActionButton(
                    onClick = {
                        onDownloadClick()
                        onExpandChange(false)
                    },
                    containerColor = SecureBlue,
                    contentColor = Color.Black,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
                
                // Browser FAB
                SmallFloatingActionButton(
                    onClick = {
                        onBrowserClick()
                        onExpandChange(false)
                    },
                    containerColor = AccentPurple,
                    contentColor = Color.Black,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Language, contentDescription = "Browser")
                }
            }
        }
        
        // Main Camera FAB with rotation animation
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 45f else 0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "fabRotation"
        )
        
        FloatingActionButton(
            onClick = {
                if (expanded) {
                    onCameraClick()
                    onExpandChange(false)
                } else {
                    onExpandChange(true)
                }
            },
            containerColor = VaultGreen,
            contentColor = Color.Black,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "Close" else "Add",
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun PremiumFileCard(
    file: VaultFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // Use remember with file.id as key to maintain stability
    val fileType = remember(file.mimeType) { FileType.fromMimeType(file.mimeType) }
    val interactionSource = remember(file.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cardScale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // File type icon with gradient background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    VaultGreenSubtle.copy(alpha = 0.5f),
                                    VaultGreenSubtle.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFileIcon(fileType),
                        contentDescription = null,
                        tint = VaultGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // File info
                Column {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${file.formattedSize} • ${formatDate(file.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Imported indicator
            if (file.isImported) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DestructiveRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Imported file",
                        tint = DestructiveRed,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * List view file card - horizontal layout for list mode
 */
@Composable
private fun ListFileCard(
    file: VaultFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val fileType = remember(file.mimeType) { FileType.fromMimeType(file.mimeType) }
    val interactionSource = remember(file.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "listCardScale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                VaultGreenSubtle.copy(alpha = 0.5f),
                                VaultGreenSubtle.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileIcon(fileType),
                    contentDescription = null,
                    tint = VaultGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // File info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "•",
                        color = TextMuted
                    )
                    Text(
                        text = formatDate(file.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (file.isImported) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Imported file",
                            tint = DestructiveRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumDeleteDialog(
    file: VaultFile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(DestructiveRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = DestructiveRed,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                "Crypto-Shred File?",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    "\"${file.fileName}\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "The encryption key will be destroyed. This action is IRREVERSIBLE.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DestructiveRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Destroy", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private fun getFileIcon(fileType: FileType): ImageVector {
    return when (fileType) {
        FileType.IMAGE -> Icons.Default.Image
        FileType.VIDEO -> Icons.Default.VideoFile
        FileType.AUDIO -> Icons.Default.AudioFile
        FileType.PDF -> Icons.Default.PictureAsPdf
        FileType.TEXT -> Icons.Default.Description
        FileType.DOCUMENT -> Icons.Default.Article
        FileType.UNKNOWN -> Icons.Default.InsertDriveFile
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("MMM d", Locale.getDefault())
    return format.format(Date(timestamp))
}
