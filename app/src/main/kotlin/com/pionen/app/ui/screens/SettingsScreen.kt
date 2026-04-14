package com.pionen.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.security.StealthManager
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Pixel-art Gen-Z Settings Screen.
 * Sharp cards · pixel headers · neon toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPanicWipe: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val vaultStats by viewModel.vaultStats.collectAsState()
    val isHardwareBacked by viewModel.isHardwareBacked.collectAsState()
    val isPinConfigured by viewModel.isPinConfigured.collectAsState()
    val currentDisguise by viewModel.currentDisguise.collectAsState()
    var showStealthDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── PIXEL HEADER ───
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .drawBehind { drawRect(Color.Black, Offset(2f, 2f), size) }
                            .background(DarkCard)
                            .border(1.dp, PixelBorderBright)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }

                    Column {
                        Text(
                            text = "SETTINGS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "system configuration",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                    Box(modifier = Modifier.width(40.dp).fillMaxHeight().background(NeonGreen))
                    Box(modifier = Modifier.fillMaxSize().background(PixelBorderBright))
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(350)) + slideInVertically(tween(350, easing = PionenEasing.EaseOut)) { it / 20 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Security Status Card
                    PixelCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            PixelSectionHeader(Icons.Default.Shield, "SECURITY STATUS", NeonGreen)

                            PixelStatusRow("Hardware Protection", if (isHardwareBacked) "ACTIVE" else "SOFTWARE ONLY", isHardwareBacked, NeonGreen)
                            PixelDivider()
                            PixelStatusRow("Encryption", "AES-256-GCM", true, NeonGreen)
                            PixelDivider()
                            PixelStatusRow("Cloud Backup", "DISABLED", true, NeonGreen)
                            PixelDivider()

                            PixelToggleRow(
                                title = "Shake to Wipe",
                                subtitle = "4x shake → emergency wipe",
                                checked = true,
                                onCheckedChange = {}
                            )

                            PixelDivider()

                            PixelClickRow(
                                title = "Security PIN",
                                subtitle = if (isPinConfigured) "PIN ACTIVE — 6 digits" else "Set 6-digit PIN",
                                isActive = isPinConfigured,
                                icon = Icons.Default.Lock,
                                onClick = { pinInput = ""; showPinDialog = true }
                            )
                        }
                    }

                    // ── Stealth Icon Card
                    PixelCard(onClick = { showStealthDialog = true }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(38.dp)
                                    .background(ElectricCyan.copy(alpha = 0.12f))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AppShortcut, null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("STEALTH ICON", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = TextPrimary)
                                Text("Active: ${currentDisguise.displayName}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                        }
                    }

                    // ── Vault Statistics Card
                    PixelCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PixelSectionHeader(Icons.Default.Folder, "VAULT STATS", ElectricCyan)
                            vaultStats?.let { stats ->
                                PixelStatusRow("Files", "${stats.fileCount}", false, NeonGreen)
                                PixelDivider()
                                PixelStatusRow("Original Size", formatSize(stats.totalOriginalSize), false, NeonGreen)
                                PixelDivider()
                                PixelStatusRow("Storage Used", formatSize(stats.vaultDirectorySize), false, NeonGreen)
                            }
                        }
                    }

                    // ── Privacy & Anonymity
                    val torConnectionState by viewModel.torConnectionState.collectAsState()
                    val torBootstrapProgress by viewModel.torBootstrapProgress.collectAsState()
                    val isTorEnabled by viewModel.isTorEnabled.collectAsState()
                    val vpnStatus by viewModel.vpnStatus.collectAsState()

                    com.pionen.app.ui.components.TorSettingsCard(
                        connectionState = torConnectionState,
                        bootstrapProgress = torBootstrapProgress,
                        isEnabled = isTorEnabled,
                        onToggle = { viewModel.toggleTor() }
                    )

                    com.pionen.app.ui.components.VpnStatusCard(
                        vpnStatus = vpnStatus,
                        onRefresh = { viewModel.refreshVpnStatus() }
                    )

                    // ── Advanced Security Card
                    PixelAdvancedSecurityCard(viewModel = viewModel, scope = scope)

                    Spacer(Modifier.height(4.dp))

                    // ── DANGER ZONE ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind { drawRect(DestructiveRedDark.copy(alpha = 0.6f), Offset(4f, 4f), size) }
                            .background(DarkCard)
                            .border(1.dp, DestructiveRed.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PixelSectionHeader(Icons.Default.Warning, "DANGER ZONE", DestructiveRed)

                            Text(
                                "Emergency wipe destroys all encryption keys. Files become IRRECOVERABLE.",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextSecondary
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind { drawRect(DestructiveRedDark, Offset(3f, 3f), size) }
                                    .background(DestructiveRed)
                                    .clickable(onClick = onPanicWipe)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.DeleteForever, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(
                                        "EMERGENCY WIPE",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Stealth Dialog
    if (showStealthDialog) {
        PixelStealthDialog(
            currentDisguise = currentDisguise,
            onDisguiseSelected = { d -> scope.launch { viewModel.switchDisguise(d) }; showStealthDialog = false },
            onDismiss = { showStealthDialog = false }
        )
    }

    // PIN Dialog
    if (showPinDialog) {
        PixelPinSetupDialog(
            isPinConfigured = isPinConfigured,
            pinInput = pinInput,
            onPinChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
            onConfirm = { scope.launch { viewModel.setPin(pinInput); showPinDialog = false } },
            onDismiss = { showPinDialog = false }
        )
    }
}

// ────────────────────────────────────────────────
// PIXEL SETTINGS COMPONENTS
// ────────────────────────────────────────────────

@Composable
private fun PixelCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(Color.Black, Offset(3f, 3f), size) }
            .background(DarkCard)
            .border(1.dp, PixelBorderBright)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
private fun PixelSectionHeader(icon: ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(30.dp).background(tint.copy(alpha = 0.12f)).border(1.dp, tint.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
            color = tint
        )
    }
}

@Composable
private fun PixelDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PixelBorderBright))
}

@Composable
private fun PixelStatusRow(label: String, value: String, isActive: Boolean, activeColor: Color = NeonGreen) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
        Box(
            modifier = Modifier
                .background(if (isActive) activeColor.copy(alpha = 0.1f) else Color.Transparent)
                .border(if (isActive) 1.dp else 0.dp, if (isActive) activeColor.copy(alpha = 0.3f) else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = if (isActive) activeColor else TextPrimary)
        }
    }
}

@Composable
private fun PixelToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
        }

        // Pixel toggle — no circles, just a flat slider
        val thumbOffset by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
            label = "toggle"
        )
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(24.dp)
                .background(if (checked) NeonGreen else DarkSurfaceVariant)
                .border(1.dp, if (checked) NeonGreenDark else PixelBorderBright)
                .clickable { onCheckedChange(!checked) }
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (22 * thumbOffset).dp)
                    .size(20.dp)
                    .background(if (checked) Color.Black else TextSecondary)
            )
        }
    }
}

@Composable
private fun PixelClickRow(title: String, subtitle: String, isActive: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = if (isActive) NeonGreen else TextSecondary)
        }
        Icon(icon, null, tint = if (isActive) NeonGreen else TextMuted)
    }
}

// ────────────────────────────────────────────────
// ADVANCED SECURITY CARD
// ────────────────────────────────────────────────
@Composable
private fun PixelAdvancedSecurityCard(viewModel: SettingsViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    val isDecoyEnabled by viewModel.isDecoyEnabled.collectAsState()
    val isIntruderCaptureEnabled by viewModel.isIntruderCaptureEnabled.collectAsState()
    val intruderCaptures by viewModel.intruderCaptures.collectAsState()
    val decoyAccessCount by viewModel.decoyAccessCount.collectAsState()

    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showIntruderCapturesDialog by remember { mutableStateOf(false) }
    var decoyPinInput by remember { mutableStateOf("") }

    PixelCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PixelSectionHeader(Icons.Default.Security, "ADVANCED SECURITY", WarningOrange)

            PixelAdvancedToggleRow(
                title = "Decoy Vault",
                subtitle = if (isDecoyEnabled) "ACTIVE · $decoyAccessCount accesses" else "Fake vault with alt PIN",
                checked = isDecoyEnabled,
                onToggle = { if (!isDecoyEnabled) { decoyPinInput = ""; showDecoyPinDialog = true } else scope.launch { viewModel.disableDecoyVault() } },
                icon = Icons.Default.ContentCopy,
                isActive = isDecoyEnabled
            )

            PixelDivider()

            PixelAdvancedToggleRow(
                title = "Intruder Photo",
                subtitle = if (isIntruderCaptureEnabled) "ACTIVE · ${intruderCaptures.size} captures" else "Photo on failed unlock",
                checked = isIntruderCaptureEnabled,
                onToggle = { scope.launch { if (!isIntruderCaptureEnabled) viewModel.enableIntruderCapture(2) else viewModel.disableIntruderCapture() } },
                icon = Icons.Default.CameraAlt,
                isActive = isIntruderCaptureEnabled
            )

            if (intruderCaptures.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DestructiveRed.copy(alpha = 0.08f))
                        .border(1.dp, DestructiveRed.copy(alpha = 0.3f))
                        .clickable { showIntruderCapturesDialog = true }
                        .padding(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = DestructiveRed, modifier = Modifier.size(16.dp))
                            Column {
                                Text("${intruderCaptures.size} INTRUDER PHOTO${if (intruderCaptures.size > 1) "S" else ""}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = DestructiveRed)
                                Text("Tap to view", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                    }
                }
            }
        }
    }

    // Decoy PIN Dialog
    if (showDecoyPinDialog) {
        AlertDialog(
            onDismissRequest = { showDecoyPinDialog = false },
            containerColor = DarkCard,
            shape = RoundedCornerShape(2.dp),
            title = { Text("SET DECOY PIN", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = WarningOrange) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Different PIN that opens a fake empty vault.", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                    Row(
                        modifier = Modifier.background(WarningOrange.copy(alpha = 0.08f)).border(1.dp, WarningOrange.copy(alpha = 0.3f)).padding(10.dp),
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                        Text("Use when forced to reveal your vault", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = WarningOrange)
                    }
                    OutlinedTextField(
                        value = decoyPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) decoyPinInput = it },
                        label = { Text("6-Digit Decoy PIN", color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(2.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WarningOrange, unfocusedBorderColor = PixelBorderBright, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = WarningOrange)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { scope.launch { viewModel.enableDecoyVault(decoyPinInput); showDecoyPinDialog = false } },
                    enabled = decoyPinInput.length == 6,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarningOrange, disabledContainerColor = WarningOrange.copy(alpha = 0.3f))
                ) { Text("ENABLE", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showDecoyPinDialog = false }) { Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = TextSecondary) }
            }
        )
    }

    // Intruder Captures Dialog
    if (showIntruderCapturesDialog) {
        AlertDialog(
            onDismissRequest = { showIntruderCapturesDialog = false },
            containerColor = DarkCard,
            shape = RoundedCornerShape(2.dp),
            title = { Text("INTRUDER CAPTURES", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = DestructiveRed) },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    intruderCaptures.forEach { capture ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(DarkSurfaceVariant).border(1.dp, PixelBorderBright).padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(formatDateTime(capture.timestamp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                                Text("Failed login attempt", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                            }
                            IconButton(onClick = { viewModel.deleteIntruderCapture(capture) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = DestructiveRed)
                            }
                        }
                    }
                    if (intruderCaptures.isEmpty()) {
                        Text("No captures recorded", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                if (intruderCaptures.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { viewModel.clearAllIntruderCaptures() } }) {
                        Text("CLEAR ALL", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = DestructiveRed)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntruderCapturesDialog = false }) { Text("Close", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun PixelAdvancedToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit, icon: ImageVector, isActive: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(if (isActive) WarningOrange.copy(alpha = 0.12f) else DarkSurfaceVariant).border(1.dp, if (isActive) WarningOrange.copy(alpha = 0.3f) else PixelBorderBright),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (isActive) WarningOrange else TextMuted, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = if (isActive) WarningOrange else TextSecondary)
            }
        }

        val thumbOffset by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
            label = "advancedToggle"
        )
        Box(
            modifier = Modifier.width(48.dp).height(24.dp)
                .background(if (checked) WarningOrange else DarkSurfaceVariant)
                .border(1.dp, if (checked) WarningOrange.copy(alpha = 0.6f) else PixelBorderBright)
                .clickable(onClick = onToggle).padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.offset(x = (22 * thumbOffset).dp).size(20.dp).background(if (checked) Color.Black else TextSecondary))
        }
    }
}

// ────────────────────────────────────────────────
// STEALTH DIALOG
// ────────────────────────────────────────────────
@Composable
private fun PixelStealthDialog(currentDisguise: StealthManager.Disguise, onDisguiseSelected: (StealthManager.Disguise) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(2.dp),
        title = { Text("APP DISGUISE", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp), color = ElectricCyan) },
        text = {
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StealthManager.Disguise.values().forEach { disguise ->
                    val isSelected = currentDisguise == disguise
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) NeonGreen.copy(alpha = 0.1f) else DarkCard)
                            .border(1.dp, if (isSelected) NeonGreen else PixelBorderBright)
                            .selectable(selected = isSelected, onClick = { onDisguiseSelected(disguise) }, role = Role.RadioButton)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = NeonGreen, unselectedColor = TextMuted))
                        Spacer(Modifier.width(10.dp))
                        Icon(getDisguiseIcon(disguise), null, tint = if (isSelected) NeonGreen else TextSecondary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(disguise.displayName, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text(disguise.description, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = TextSecondary) }
        }
    )
}

// ────────────────────────────────────────────────
// PIN SETUP DIALOG
// ────────────────────────────────────────────────
@Composable
private fun PixelPinSetupDialog(isPinConfigured: Boolean, pinInput: String, onPinChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(2.dp),
        title = {
            Text(
                if (isPinConfigured) "CHANGE PIN" else "SET PIN",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                color = NeonGreen
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("6-digit PIN required after biometric unlock.", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = onPinChange,
                    label = { Text("6-Digit PIN", color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(2.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen, unfocusedBorderColor = PixelBorderBright, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = NeonGreen)
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.drawBehind { if (pinInput.length == 6) drawRect(NeonGreenDark, Offset(3f, 3f), size) }
                    .background(if (pinInput.length == 6) NeonGreen else NeonGreen.copy(alpha = 0.3f))
                    .clickable(enabled = pinInput.length == 6, onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("SAVE", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = TextSecondary) }
        }
    )
}

// ────────────────────────────────────────────────
// UTILITIES
// ────────────────────────────────────────────────
private fun getDisguiseIcon(disguise: StealthManager.Disguise): ImageVector = when (disguise) {
    StealthManager.Disguise.DEFAULT -> Icons.Default.Lock
    StealthManager.Disguise.CALCULATOR -> Icons.Default.Calculate
    StealthManager.Disguise.NOTES -> Icons.Default.Notes
    StealthManager.Disguise.UTILITIES -> Icons.Default.Settings
}

private fun formatDateTime(timestamp: Long): String =
    java.text.SimpleDateFormat("MMM dd, yyyy · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
