package com.pionen.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pionen.app.core.vault.FileType
import com.pionen.app.core.vault.VaultFile
import com.pionen.app.ui.theme.*
import java.util.UUID

/**
 * Premium floating action bar with glassmorphism design.
 * Features: Search, Recent files, Download options, Filters, View toggle
 */
@Composable
fun FloatingActionBar(
    files: List<VaultFile>,
    selectedFilter: FileTypeFilter,
    onFilterChange: (FileTypeFilter) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRecentFileClick: (UUID) -> Unit,
    onDownloadFromWeb: () -> Unit,
    onUrlDownload: () -> Unit,
    onCameraClick: () -> Unit,
    onWebAccessClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isGridView: Boolean,
    onViewToggle: () -> Unit,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit,
    isServerRunning: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showRecentDropdown by remember { mutableStateOf(false) }
    var showDownloadDropdown by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    
    // Get recent files (last 5 accessed, sorted by date)
    val recentFiles = remember(files) {
        files.sortedByDescending { it.createdAt }.take(5)
    }
    
    // Entrance animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + slideInVertically(
            animationSpec = tween(500, easing = PionenEasing.EaseOut),
            initialOffsetY = { it }  // Slide in from bottom
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp)  // Extra bottom padding for nav bar
        ) {
            // Main floating bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = VaultGreen.copy(alpha = 0.1f),
                        spotColor = VaultGreen.copy(alpha = 0.15f)
                    ),
                shape = RoundedCornerShape(20.dp),
                color = FloatingBarBackground,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            FloatingBarBorder,
                            FloatingBarBorder.copy(alpha = 0.3f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Top row: Search, Recent, Download, Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search field (expandable)
                        AnimatedSearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            isExpanded = isSearchExpanded,
                            onExpandToggle = { isSearchExpanded = !isSearchExpanded },
                            modifier = Modifier.weight(if (isSearchExpanded) 1f else 0.4f)
                        )
                        
                        if (!isSearchExpanded) {
                            // Camera quick access button
                            CameraQuickAccessButton(
                                onClick = onCameraClick
                            )
                            
                            // Web Access button
                            WebAccessButton(
                                onClick = onWebAccessClick,
                                isActive = isServerRunning
                            )
                            
                            // Recent Files dropdown
                            Box {
                                ActionChip(
                                    icon = Icons.Default.History,
                                    label = "Recent",
                                    onClick = { showRecentDropdown = true }
                                )
                                
                                DropdownMenu(
                                    expanded = showRecentDropdown,
                                    onDismissRequest = { showRecentDropdown = false },
                                    modifier = Modifier
                                        .background(DarkCard)
                                        .widthIn(min = 200.dp)
                                ) {
                                    if (recentFiles.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    "No recent files",
                                                    color = TextMuted
                                                )
                                            },
                                            onClick = { showRecentDropdown = false }
                                        )
                                    } else {
                                        recentFiles.forEach { file ->
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = getFileTypeIcon(FileType.fromMimeType(file.mimeType)),
                                                        contentDescription = null,
                                                        tint = VaultGreen,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                text = {
                                                    Text(
                                                        file.fileName,
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                onClick = {
                                                    onRecentFileClick(file.id)
                                                    showRecentDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Download dropdown
                            Box {
                                ActionChip(
                                    icon = Icons.Default.Download,
                                    label = "Download",
                                    onClick = { showDownloadDropdown = true },
                                    accentColor = SecureBlue
                                )
                                
                                DropdownMenu(
                                    expanded = showDownloadDropdown,
                                    onDismissRequest = { showDownloadDropdown = false },
                                    modifier = Modifier.background(DarkCard)
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Language,
                                                contentDescription = null,
                                                tint = AccentPurple
                                            )
                                        },
                                        text = {
                                            Column {
                                                Text("Download from Web", color = TextPrimary)
                                                Text(
                                                    "Browse & download securely",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextMuted
                                                )
                                            }
                                        },
                                        onClick = {
                                            onDownloadFromWeb()
                                            showDownloadDropdown = false
                                        }
                                    )
                                    Divider(color = GlassBorder.copy(alpha = 0.3f))
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Link,
                                                contentDescription = null,
                                                tint = SecureBlue
                                            )
                                        },
                                        text = {
                                            Column {
                                                Text("URL Download", color = TextPrimary)
                                                Text(
                                                    "Direct link download",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextMuted
                                                )
                                            }
                                        },
                                        onClick = {
                                            onUrlDownload()
                                            showDownloadDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Bottom row: Filter chips + View/Sort options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Filter chips (scrollable)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FileTypeFilter.entries.forEach { filter ->
                                FilterChip(
                                    filter = filter,
                                    isSelected = selectedFilter == filter,
                                    onClick = { onFilterChange(filter) }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // View toggle + Sort
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // View toggle (Grid/List)
                            IconToggleButton(
                                checked = isGridView,
                                onCheckedChange = { onViewToggle() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.GridView else Icons.Default.ViewList,
                                    contentDescription = if (isGridView) "Grid view" else "List view",
                                    tint = if (isGridView) VaultGreen else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Sort dropdown
                            Box {
                                IconButton(
                                    onClick = { showSortDropdown = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = showSortDropdown,
                                    onDismissRequest = { showSortDropdown = false },
                                    modifier = Modifier.background(DarkCard)
                                ) {
                                    SortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                if (sortOption == option) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = VaultGreen
                                                    )
                                                }
                                            },
                                            text = {
                                                Text(
                                                    option.label,
                                                    color = if (sortOption == option) VaultGreen else TextPrimary
                                                )
                                            },
                                            onClick = {
                                                onSortChange(option)
                                                showSortDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Settings button
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated expandable search field
 */
@Composable
private fun AnimatedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = TextMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onExpandToggle() }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                singleLine = true,
                cursorBrush = SolidColor(VaultGreen),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Search files...",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Action chip for dropdowns
 */
@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accentColor: Color = VaultGreen,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "chipScale"
    )
    
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Camera quick access button - prominent FAB-like button
 */
@Composable
private fun CameraQuickAccessButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cameraScale"
    )
    
    Surface(
        modifier = modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = VaultGreen.copy(alpha = 0.2f),
                spotColor = VaultGreen.copy(alpha = 0.3f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = VaultGreen
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Camera",
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Web Access button - shows server status
 */
@Composable
private fun WebAccessButton(
    onClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "webAccessScale"
    )
    
    // Pulsing animation when server is active
    val infiniteTransition = rememberInfiniteTransition(label = "serverPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Surface(
        modifier = modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = if (isActive) SecureBlue.copy(alpha = 0.3f) else TextMuted.copy(alpha = 0.1f),
                spotColor = if (isActive) SecureBlue.copy(alpha = 0.4f) else TextMuted.copy(alpha = 0.2f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (isActive) SecureBlue else DarkSurface,
        border = if (!isActive) androidx.compose.foundation.BorderStroke(
            1.dp,
            GlassBorder
        ) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = "Web Access",
                tint = if (isActive) Color.White.copy(alpha = pulseAlpha) else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Filter chip for file types
 */
@Composable
private fun FilterChip(
    filter: FileTypeFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "filterScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) VaultGreen.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(200),
        label = "filterBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) VaultGreen else GlassBorder.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "filterBorder"
    )
    
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                filter.icon,
                contentDescription = null,
                tint = if (isSelected) VaultGreen else TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                filter.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) VaultGreen else TextSecondary
            )
        }
    }
}

/**
 * Floating settings button for top-right corner
 */
@Composable
fun FloatingSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "settingsScale"
    )
    
    // Entrance animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400, delayMillis = 200)) + scaleIn(
            animationSpec = tween(400, delayMillis = 200, easing = PionenEasing.EaseOutBack)
        ),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = VaultGreen.copy(alpha = 0.1f)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            shape = CircleShape,
            color = DarkCard,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                GlassBorder.copy(alpha = 0.3f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * File type filter enum
 */
enum class FileTypeFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Outlined.Folder),
    IMAGES("Images", Icons.Outlined.Image),
    VIDEOS("Videos", Icons.Outlined.VideoFile),
    AUDIO("Audio", Icons.Outlined.AudioFile),
    DOCUMENTS("Docs", Icons.Outlined.Description)
}

/**
 * Sort options enum
 */
enum class SortOption(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first")
}

/**
 * Get icon for file type
 */
private fun getFileTypeIcon(fileType: FileType): ImageVector {
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
