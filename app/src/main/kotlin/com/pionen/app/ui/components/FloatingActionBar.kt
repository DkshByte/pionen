package com.pionen.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pionen.app.core.vault.FileType
import com.pionen.app.core.vault.VaultFile
import com.pionen.app.ui.theme.*
import java.util.UUID

// ============================================
// PIXEL BOTTOM NAV BAR
// Flat bar · 2px neon top border · pixel icons
// Based on the provided pixel-style bottom bar
// ============================================

/**
 * Pixel-art bottom navigation bar.
 * Flat dark bar with a neon green 2px top border.
 * Three tabs: Home · Camera (center featured) · Files
 */
@Composable
fun MinimalBottomBar(
    showDashboard: Boolean,
    onHomeClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300, delayMillis = 150)) + slideInVertically(
            animationSpec = tween(400, delayMillis = 150, easing = PionenEasing.EaseOut),
            initialOffsetY = { it }
        ),
        modifier = modifier
    ) {
        Column {
            // 2px neon green pixel border top line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(NeonGreen)
            )

            // Main bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PixelBarBackground)
                    .navigationBarsPadding()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelNavItem(
                    icon = Icons.Outlined.Home,
                    activeIcon = Icons.Filled.Home,
                    label = "HOME",
                    isActive = showDashboard,
                    onClick = onHomeClick
                )

                PixelCameraNavButton(onClick = onCameraClick)

                PixelNavItem(
                    icon = Icons.Outlined.Folder,
                    activeIcon = Icons.Filled.Folder,
                    label = "FILES",
                    isActive = !showDashboard,
                    onClick = onFilesClick
                )
            }
        }
    }
}

@Composable
private fun PixelNavItem(
    icon: ImageVector,
    activeIcon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val tintColor by animateColorAsState(
        targetValue = if (isActive) NeonGreen else TextSecondary,
        animationSpec = tween(150),
        label = "navTint"
    )

    Column(
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = if (isActive) activeIcon else icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 1.sp
            ),
            color = tintColor
        )
        // Active indicator — 3px pixel dot
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 2.dp)
                    .background(NeonGreen)
            )
        }
    }
}

@Composable
private fun PixelCameraNavButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(52.dp)
            .drawBehind {
                if (!isPressed) {
                    drawRect(
                        color = NeonGreenDark,
                        topLeft = Offset(3f, 3f),
                        size = size
                    )
                }
            }
            .background(NeonGreen)
            .border(1.dp, Color.Black.copy(alpha = 0.2f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = "Camera",
            tint = Color.Black,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ────────────────────────────────────────────────
// LEGACY STUBS — Keep backward compatibility
// ────────────────────────────────────────────────

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
) {}

@Composable
fun FloatingSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {}

// ────────────────────────────────────────────────
// ENUMS
// ────────────────────────────────────────────────

enum class FileTypeFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Outlined.Folder),
    IMAGES("Images", Icons.Outlined.Image),
    VIDEOS("Videos", Icons.Outlined.VideoFile),
    AUDIO("Audio", Icons.Outlined.AudioFile),
    DOCUMENTS("Docs", Icons.Outlined.Description)
}

enum class SortOption(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first")
}

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
