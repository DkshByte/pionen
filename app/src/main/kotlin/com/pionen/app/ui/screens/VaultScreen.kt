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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.vault.FileType
import com.pionen.app.core.vault.VaultFile
import com.pionen.app.ui.components.FileTypeFilter
import com.pionen.app.ui.components.MinimalBottomBar
import com.pionen.app.ui.components.SortOption
import com.pionen.app.ui.components.WebAccessDialog
import com.pionen.app.server.SecureWebServer
import com.pionen.app.ui.components.*
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.VaultViewModel
import com.pionen.app.ui.viewmodels.WebServerViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ============================================
// VAULT SCREEN — Pixel Art Gen-Z
// Dark grid · pixel icons · neon accents
// ============================================

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

    val serverState by webServerViewModel.serverState.collectAsState()
    val serverInfo by webServerViewModel.serverInfo.collectAsState()
    val qrCodeBitmap by webServerViewModel.qrCodeBitmap.collectAsState()
    val showWebAccessDialog by webServerViewModel.showDialog.collectAsState()

    var showDashboard by remember { mutableStateOf(true) }

    var selectedFilter by remember { mutableStateOf(FileTypeFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }

    val filteredFiles = remember(files, selectedFilter, searchQuery, sortOption) {
        files
            .filter { file ->
                when (selectedFilter) {
                    FileTypeFilter.ALL -> true
                    FileTypeFilter.IMAGES -> file.mimeType.startsWith("image/")
                    FileTypeFilter.VIDEOS -> file.mimeType.startsWith("video/")
                    FileTypeFilter.AUDIO -> file.mimeType.startsWith("audio/")
                    FileTypeFilter.DOCUMENTS -> file.mimeType.startsWith("application/") || file.mimeType.startsWith("text/")
                }
            }
            .filter { file -> searchQuery.isEmpty() || file.fileName.contains(searchQuery, ignoreCase = true) }
            .let { filtered ->
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 66.dp)  // Space for bottom nav (64dp bar + 2dp border)
        ) {
            // ─── TOP HEADER ───
            PixelDashboardHeader(
                fileCount = vaultStats?.fileCount ?: 0,
                showDashboard = showDashboard,
                onSettingsClick = onSettingsClick,
                onBackToGrid = { showDashboard = true }
            )

            // ─── CONTENT ───
            AnimatedContent(
                targetState = showDashboard,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(250)) + slideInHorizontally { -it / 4 }) togetherWith
                                (fadeOut(tween(180)) + slideOutHorizontally { it / 4 })
                    } else {
                        (fadeIn(tween(250)) + slideInHorizontally { it / 4 }) togetherWith
                                (fadeOut(tween(180)) + slideOutHorizontally { -it / 4 })
                    }
                },
                label = "viewSwitch"
            ) { isDashboard ->
                if (isDashboard) {
                    PixelDashboardGrid(
                        fileCount = files.size,
                        totalSize = vaultStats?.totalOriginalSize ?: 0L,
                        onVaultFilesClick = { showDashboard = false },
                        onCameraClick = onCameraClick,
                        onGalleryClick = { if (files.isNotEmpty()) onGalleryClick(0) else showDashboard = false },
                        onBrowserClick = onBrowserClick,
                        onDownloadClick = onDownloadClick,
                        onWebAccessClick = { webServerViewModel.showDialog() },
                        isServerRunning = serverState is SecureWebServer.ServerState.Running
                    )
                } else {
                    PixelFileBrowserView(
                        files = files,
                        filteredFiles = filteredFiles,
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isGridView = isGridView,
                        onViewToggle = { isGridView = !isGridView },
                        sortOption = sortOption,
                        onSortChange = { sortOption = it },
                        onFileClick = onFileClick,
                        onGalleryClick = onGalleryClick,
                        onDelete = { showDeleteDialog = it }
                    )
                }
            }
        }

        // Bottom nav bar
        MinimalBottomBar(
            showDashboard = showDashboard,
            onHomeClick = { showDashboard = true },
            onCameraClick = onCameraClick,
            onFilesClick = { showDashboard = false },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

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

    showDeleteDialog?.let { file ->
        PixelDeleteDialog(
            file = file,
            onConfirm = { viewModel.deleteFile(file.id); showDeleteDialog = null },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

// ─── HEADER ────────────────────────────────────
@Composable
private fun PixelDashboardHeader(
    fileCount: Int,
    showDashboard: Boolean,
    onSettingsClick: () -> Unit,
    onBackToGrid: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(
                    visible = !showDashboard,
                    enter = fadeIn(tween(200)) + scaleIn(tween(200)),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150))
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(DarkCard)
                            .border(1.dp, PixelBorderBright)
                            .clickable(onClick = onBackToGrid),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonGreen, modifier = Modifier.size(18.dp))
                    }
                }

                Column {
                    Text(
                        text = if (showDashboard) "PIONEN" else "FILES",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        ),
                        color = if (showDashboard) NeonGreen else TextPrimary
                    )
                    if (showDashboard && fileCount > 0) {
                        Text(
                            text = "$fileCount files secured",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                    }
                }
            }

            // Settings — pixel square button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .drawBehind {
                        drawRect(Color.Black, Offset(2f, 2f), size)
                    }
                    .background(DarkCard)
                    .border(1.dp, PixelBorderBright)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        // Pixel horizontal rule — neon + dark combo
        Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            Box(modifier = Modifier.width(40.dp).fillMaxHeight().background(NeonGreen))
            Box(modifier = Modifier.fillMaxSize().background(PixelBorderBright))
        }
    }
}

// ─── DASHBOARD GRID ────────────────────────────
@Composable
private fun PixelDashboardGrid(
    fileCount: Int,
    totalSize: Long,
    onVaultFilesClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onBrowserClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWebAccessClick: () -> Unit,
    isServerRunning: Boolean
) {
    data class CardData(val icon: ImageVector, val title: String, val subtitle: String, val accent: Color)

    val cards = listOf(
        CardData(Icons.Outlined.Lock, "VAULT", "$fileCount files", NeonGreen),
        CardData(Icons.Outlined.CameraAlt, "CAMERA", "Capture", NeonGreen),
        CardData(Icons.Outlined.PhotoLibrary, "GALLERY", "View media", ElectricCyan),
        CardData(Icons.Outlined.Language, "BROWSER", "Secure surf", NeonPurple),
        CardData(Icons.Outlined.Download, "DOWNLOAD", "Get files", ElectricCyan),
        CardData(
            Icons.Outlined.Wifi,
            "WEB ACCESS",
            if (isServerRunning) "● LIVE" else "Transfer",
            if (isServerRunning) SafeGreen else NeonPurple
        )
    )

    val callbacks = listOf(
        onVaultFilesClick, onCameraClick, onGalleryClick,
        onBrowserClick, onDownloadClick, onWebAccessClick
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(cards) { index, card ->
            var isVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(index * 60L); isVisible = true }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(350)) + slideInVertically(tween(350, easing = PionenEasing.EaseOut)) { it / 2 }
            ) {
                PixelDashboardCard(
                    icon = card.icon,
                    title = card.title,
                    subtitle = card.subtitle,
                    accent = card.accent,
                    onClick = callbacks[index]
                )
            }
        }
    }
}

@Composable
private fun PixelDashboardCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawBehind {
                if (!isPressed) {
                    drawRect(Color.Black, Offset(4f, 4f), size)
                }
            }
            .background(DarkCard)
            .border(1.dp, PixelBorderBright)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        // Accent corner
        Box(
            modifier = Modifier
                .size(4.dp)
                .align(Alignment.TopStart)
                .background(accent)
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .align(Alignment.TopEnd)
                .background(accent)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon box — pixel style
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }
        }
    }
}

// ─── FILE BROWSER ──────────────────────────────
@Composable
private fun PixelFileBrowserView(
    files: List<VaultFile>,
    filteredFiles: List<VaultFile>,
    selectedFilter: FileTypeFilter,
    onFilterChange: (FileTypeFilter) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isGridView: Boolean,
    onViewToggle: () -> Unit,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit,
    onFileClick: (UUID) -> Unit,
    onGalleryClick: (Int) -> Unit,
    onDelete: (VaultFile) -> Unit
) {
    var showSortDropdown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .drawBehind {
                            if (isSelected) drawRect(NeonGreenDark, Offset(2f, 2f), size)
                        }
                        .background(if (isSelected) NeonGreen.copy(alpha = 0.15f) else DarkCard)
                        .border(1.dp, if (isSelected) NeonGreen else PixelBorderBright)
                        .clickable { onFilterChange(filter) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = filter.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) NeonGreen else TextSecondary
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // View toggle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(DarkCard)
                    .border(1.dp, PixelBorderBright)
                    .clickable(onClick = onViewToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGridView) Icons.Default.GridView else Icons.Default.ViewList,
                    contentDescription = "Toggle view",
                    tint = NeonGreen,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Sort
            Box {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(DarkCard)
                        .border(1.dp, PixelBorderBright)
                        .clickable { showSortDropdown = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                DropdownMenu(
                    expanded = showSortDropdown,
                    onDismissRequest = { showSortDropdown = false },
                    modifier = Modifier.background(DarkCard)
                ) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            leadingIcon = {
                                if (sortOption == option) Icon(Icons.Default.Check, null, tint = NeonGreen)
                            },
                            text = {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (sortOption == option) NeonGreen else TextPrimary
                                )
                            },
                            onClick = { onSortChange(option); showSortDropdown = false }
                        )
                    }
                }
            }
        }

        // File list
        Box(modifier = Modifier.weight(1f)) {
            if (filteredFiles.isEmpty()) {
                if (files.isEmpty()) PixelEmptyVaultState()
                else PixelNoResultsState(onClearFilter = { onFilterChange(FileTypeFilter.ALL); onSearchQueryChange("") })
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(items = filteredFiles, key = { _, f -> f.id }) { index, file ->
                        var isVisible by remember(file.id) { mutableStateOf(false) }
                        LaunchedEffect(file.id) { delay(staggeredDelay(index).toLong()); isVisible = true }
                        val alpha by animateFloatAsState(
                            targetValue = if (isVisible) 1f else 0f,
                            animationSpec = tween(280),
                            label = "gridAlpha$index"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isVisible) 1f else 0.92f,
                            animationSpec = tween(280),
                            label = "gridScale$index"
                        )
                        Box(modifier = Modifier.graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }) {
                            PixelGridFileCard(
                                file = file,
                                onClick = {
                                    val ft = FileType.fromMimeType(file.mimeType)
                                    val isViewable = ft == FileType.IMAGE || ft == FileType.VIDEO || ft == FileType.TEXT
                                    if (isViewable) {
                                        val viewable = filteredFiles.filter { f -> val t = FileType.fromMimeType(f.mimeType); t == FileType.IMAGE || t == FileType.VIDEO || t == FileType.TEXT }
                                        onGalleryClick(viewable.indexOfFirst { it.id == file.id }.coerceAtLeast(0))
                                    } else onFileClick(file.id)
                                },
                                onDelete = { onDelete(file) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lazyItemsIndexed(items = filteredFiles, key = { _, f -> f.id }) { index, file ->
                        var isVisible by remember(file.id) { mutableStateOf(false) }
                        LaunchedEffect(file.id) { delay(staggeredDelay(index).toLong()); isVisible = true }
                        val alpha by animateFloatAsState(
                            targetValue = if (isVisible) 1f else 0f,
                            animationSpec = tween(260),
                            label = "listAlpha$index"
                        )
                        val slideX by animateFloatAsState(
                            targetValue = if (isVisible) 0f else -40f,
                            animationSpec = tween(260, easing = PionenEasing.EaseOut),
                            label = "listSlide$index"
                        )
                        Box(modifier = Modifier.graphicsLayer { this.alpha = alpha; translationX = slideX }) {
                            PixelListFileCard(
                                file = file,
                                onClick = {
                                    val ft = FileType.fromMimeType(file.mimeType)
                                    val isViewable = ft == FileType.IMAGE || ft == FileType.VIDEO || ft == FileType.TEXT
                                    if (isViewable) {
                                        val viewable = filteredFiles.filter { f -> val t = FileType.fromMimeType(f.mimeType); t == FileType.IMAGE || t == FileType.VIDEO || t == FileType.TEXT }
                                        onGalleryClick(viewable.indexOfFirst { it.id == file.id }.coerceAtLeast(0))
                                    } else onFileClick(file.id)
                                },
                                onDelete = { onDelete(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── FILE CARDS ────────────────────────────────
@Composable
private fun PixelGridFileCard(file: VaultFile, onClick: () -> Unit, onDelete: () -> Unit) {
    val fileType = remember(file.mimeType) { FileType.fromMimeType(file.mimeType) }
    val accent = fileTypeAccent(fileType)
    val interactionSource = remember(file.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawBehind { if (!isPressed) drawRect(Color.Black, Offset(3f, 3f), size) }
            .background(DarkCard)
            .border(1.dp, PixelBorderBright)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(getFileIcon(fileType), null, tint = accent, modifier = Modifier.size(22.dp))
            }

            Column {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.formattedSize}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
            Icon(Icons.Default.Delete, "Delete", tint = TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PixelListFileCard(file: VaultFile, onClick: () -> Unit, onDelete: () -> Unit) {
    val fileType = remember(file.mimeType) { FileType.fromMimeType(file.mimeType) }
    val accent = fileTypeAccent(fileType)
    val interactionSource = remember(file.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { if (!isPressed) drawRect(Color.Black, Offset(3f, 3f), size) }
            .background(DarkCard)
            .border(1.dp, PixelBorderBright)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left accent bar
        Box(modifier = Modifier.width(3.dp).height(36.dp).background(accent))

        Box(
            modifier = Modifier.size(36.dp).background(accent.copy(alpha = 0.1f)).border(1.dp, accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(getFileIcon(fileType), null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(file.fileName, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${file.formattedSize} · ${formatDate(file.createdAt)}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

// ─── EMPTY STATES ──────────────────────────────
@Composable
private fun PixelEmptyVaultState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(80.dp).background(DarkCard).border(2.dp, NeonGreen.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(44.dp), tint = TextMuted)
        }
        Spacer(Modifier.height(20.dp))
        Text("VAULT EMPTY", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text("Capture or download to secure files", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextMuted)
    }
}

@Composable
private fun PixelNoResultsState(onClearFilter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(64.dp).background(DarkCard).border(1.dp, PixelBorderBright), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SearchOff, null, Modifier.size(36.dp), tint = TextMuted)
        }
        Spacer(Modifier.height(16.dp))
        Text("NO RESULTS", style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.drawBehind { drawRect(NeonGreenDark, Offset(3f, 3f), size) }
                .background(NeonGreen).border(1.dp, Color.Black.copy(alpha = 0.2f))
                .clickable(onClick = onClearFilter).padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("CLEAR FILTER", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = Color.Black)
        }
    }
}

// ─── DELETE DIALOG ─────────────────────────────
@Composable
private fun PixelDeleteDialog(file: VaultFile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(2.dp),
        title = {
            Text("CRYPTO-SHRED?", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = DestructiveRed)
        },
        text = {
            Column {
                Text("\"${file.fileName}\"", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text("The encryption key will be destroyed. This is IRREVERSIBLE.", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.background(DestructiveRed).border(1.dp, Color.Black.copy(alpha = 0.3f)).clickable(onClick = onConfirm).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("DESTROY", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = Color.White)
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier.background(DarkCard).border(1.dp, PixelBorderBright).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("CANCEL", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
            }
        }
    )
}

// ─── UTILITIES ─────────────────────────────────
private fun fileTypeAccent(fileType: FileType): Color = when (fileType) {
    FileType.IMAGE -> ElectricCyan
    FileType.VIDEO -> NeonPurple
    FileType.AUDIO -> WarningOrange
    FileType.PDF -> DestructiveRed
    FileType.TEXT -> NeonGreen
    FileType.DOCUMENT -> NeonGreen
    FileType.UNKNOWN -> TextSecondary
}

private fun getFileIcon(fileType: FileType): ImageVector = when (fileType) {
    FileType.IMAGE -> Icons.Default.Image
    FileType.VIDEO -> Icons.Default.VideoFile
    FileType.AUDIO -> Icons.Default.AudioFile
    FileType.PDF -> Icons.Default.PictureAsPdf
    FileType.TEXT -> Icons.Default.Description
    FileType.DOCUMENT -> Icons.Default.Article
    FileType.UNKNOWN -> Icons.Default.InsertDriveFile
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
