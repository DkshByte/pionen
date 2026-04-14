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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.core.security.StealthManager
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Premium Settings screen with refined cards and smooth animations.
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
    
    // Screen entrance animation
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
                        "Settings",
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Security Status Card
                PremiumCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Shield,
                            title = "Security Status",
                            iconTint = VaultGreen
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        StatusRow(
                            label = "Hardware Protection",
                            value = if (isHardwareBacked) "Active" else "Software Only",
                            isActive = isHardwareBacked
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        StatusRow(
                            label = "Encryption",
                            value = "AES-256-GCM",
                            isActive = true
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        StatusRow(
                            label = "Cloud Backup",
                            value = "Disabled",
                            isActive = true
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = GlassBorder.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Shake-to-Wipe toggle
                        SettingsToggleRow(
                            title = "Shake to Wipe",
                            subtitle = "Shake phone 4x to emergency wipe",
                            checked = true,
                            onCheckedChange = { /* TODO */ }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = GlassBorder.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Security PIN
                        SettingsClickableRow(
                            title = "Security PIN",
                            subtitle = if (isPinConfigured) "PIN is active (Required for login)" else "Set 6-digit PIN",
                            isActive = isPinConfigured,
                            icon = Icons.Default.Lock,
                            onClick = {
                                pinInput = ""
                                showPinDialog = true
                            }
                        )
                    }
                }
                
                // Stealth Icon Card
                PremiumCard(
                    onClick = { showStealthDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SecureBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AppShortcut,
                                contentDescription = null,
                                tint = SecureBlue
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stealth Icon",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Current: ${currentDisguise.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }
                
                // Vault Statistics Card
                PremiumCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Folder,
                            title = "Vault Statistics",
                            iconTint = SecureBlue
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        vaultStats?.let { stats ->
                            StatusRow(label = "Files", value = "${stats.fileCount}")
                            Spacer(modifier = Modifier.height(12.dp))
                            StatusRow(label = "Original Size", value = formatSize(stats.totalOriginalSize))
                            Spacer(modifier = Modifier.height(12.dp))
                            StatusRow(label = "Storage Used", value = formatSize(stats.vaultDirectorySize))
                        }
                    }
                }
                
                // Privacy & Anonymity Section
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
                
                // Advanced Security Card
                AdvancedSecurityCard(
                    viewModel = viewModel,
                    scope = scope
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Danger Zone Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DestructiveRed.copy(alpha = 0.08f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Warning,
                            title = "Danger Zone",
                            iconTint = DestructiveRed
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Emergency wipe will permanently destroy all encryption keys. All files will become irrecoverable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = onPanicWipe,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DestructiveRed
                            )
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Emergency Wipe", color = Color.White)
                        }
                    }
                }
            }
        }
    }
    
    // Stealth Icon Selection Dialog
    if (showStealthDialog) {
        PremiumStealthDialog(
            currentDisguise = currentDisguise,
            onDisguiseSelected = { disguise ->
                scope.launch {
                    viewModel.switchDisguise(disguise)
                }
                showStealthDialog = false
            },
            onDismiss = { showStealthDialog = false }
        )
    }
    
    // Set PIN Dialog
    if (showPinDialog) {
        PremiumPinSetupDialog(
            isPinConfigured = isPinConfigured,
            pinInput = pinInput,
            onPinChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
            onConfirm = {
                scope.launch {
                    viewModel.setPin(pinInput)
                    showPinDialog = false
                }
            },
            onDismiss = { showPinDialog = false }
        )
    }
}

@Composable
private fun PremiumCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    iconTint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isActive: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) VaultGreen else TextPrimary
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        // Custom animated switch
        val thumbOffset by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "switch"
        )
        
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) VaultGreen else DarkSurfaceVariant)
                .clickable { onCheckedChange(!checked) }
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (24 * thumbOffset).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
    isActive: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) VaultGreen else TextSecondary
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) VaultGreen else TextMuted
        )
    }
}

@Composable
private fun PremiumStealthDialog(
    currentDisguise: StealthManager.Disguise,
    onDisguiseSelected: (StealthManager.Disguise) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                "Choose App Disguise",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                StealthManager.Disguise.values().forEach { disguise ->
                    val isSelected = currentDisguise == disguise
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) Modifier.background(VaultGreenSubtle.copy(alpha = 0.3f))
                                else Modifier
                            )
                            .selectable(
                                selected = isSelected,
                                onClick = { onDisguiseSelected(disguise) },
                                role = Role.RadioButton
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VaultGreen
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = getDisguiseIcon(disguise),
                            contentDescription = null,
                            tint = if (isSelected) VaultGreen else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = disguise.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = disguise.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun PremiumPinSetupDialog(
    isPinConfigured: Boolean,
    pinInput: String,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                if (isPinConfigured) "Change PIN" else "Set Security PIN",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Enter a 6-digit PIN to enhance security. This will be required after biometric unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = onPinChange,
                    label = { Text("6-Digit PIN", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultGreen,
                        unfocusedBorderColor = GlassBorder.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = VaultGreen
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = pinInput.length == 6,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultGreen,
                    disabledContainerColor = VaultGreen.copy(alpha = 0.3f)
                )
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AdvancedSecurityCard(
    viewModel: SettingsViewModel,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val isDecoyEnabled by viewModel.isDecoyEnabled.collectAsState()
    val isIntruderCaptureEnabled by viewModel.isIntruderCaptureEnabled.collectAsState()
    val intruderCaptures by viewModel.intruderCaptures.collectAsState()
    val decoyAccessCount by viewModel.decoyAccessCount.collectAsState()
    
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showIntruderCapturesDialog by remember { mutableStateOf(false) }
    var decoyPinInput by remember { mutableStateOf("") }
    
    PremiumCard {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(
                icon = Icons.Default.Security,
                title = "Advanced Security",
                iconTint = WarningOrange
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Decoy Vault Toggle
            AdvancedSecurityToggleRow(
                title = "Decoy Vault",
                subtitle = if (isDecoyEnabled) "Active • $decoyAccessCount accesses" else "Fake vault with alternate PIN",
                checked = isDecoyEnabled,
                onToggle = {
                    if (!isDecoyEnabled) {
                        decoyPinInput = ""
                        showDecoyPinDialog = true
                    } else {
                        scope.launch { viewModel.disableDecoyVault() }
                    }
                },
                icon = Icons.Default.ContentCopy,
                isActive = isDecoyEnabled
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = GlassBorder.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Intruder Photo Capture Toggle
            AdvancedSecurityToggleRow(
                title = "Intruder Photo",
                subtitle = if (isIntruderCaptureEnabled) "Active • ${intruderCaptures.size} captures" else "Photo on failed unlock attempts",
                checked = isIntruderCaptureEnabled,
                onToggle = {
                    scope.launch {
                        if (!isIntruderCaptureEnabled) {
                            viewModel.enableIntruderCapture(2)
                        } else {
                            viewModel.disableIntruderCapture()
                        }
                    }
                },
                icon = Icons.Default.CameraAlt,
                isActive = isIntruderCaptureEnabled
            )
            
            // Show intruder captures if any exist
            if (intruderCaptures.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DestructiveRed.copy(alpha = 0.08f))
                        .clickable { showIntruderCapturesDialog = true }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DestructiveRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${intruderCaptures.size} Intruder Photo${if (intruderCaptures.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DestructiveRed
                            )
                            Text(
                                text = "Tap to view",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }
        }
    }
    
    // Decoy PIN Setup Dialog
    if (showDecoyPinDialog) {
        AlertDialog(
            onDismissRequest = { showDecoyPinDialog = false },
            containerColor = DarkCard,
            title = {
                Text(
                    "Set Decoy PIN",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Enter a 6-digit PIN that will open a fake empty vault. Different from your real PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarningOrange.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Use this PIN when forced to reveal your vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = decoyPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) decoyPinInput = it },
                        label = { Text("6-Digit Decoy PIN", color = TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarningOrange,
                            unfocusedBorderColor = GlassBorder.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = WarningOrange
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.enableDecoyVault(decoyPinInput)
                            showDecoyPinDialog = false
                        }
                    },
                    enabled = decoyPinInput.length == 6,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningOrange,
                        disabledContainerColor = WarningOrange.copy(alpha = 0.3f)
                    )
                ) {
                    Text("Enable", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDecoyPinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
    
    // Intruder Captures Dialog
    if (showIntruderCapturesDialog) {
        AlertDialog(
            onDismissRequest = { showIntruderCapturesDialog = false },
            containerColor = DarkCard,
            title = {
                Text(
                    "Intruder Captures",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    intruderCaptures.forEach { capture ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatDateTime(capture.timestamp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Captured on failed login",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteIntruderCapture(capture) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = DestructiveRed
                                )
                            }
                        }
                    }
                    
                    if (intruderCaptures.isEmpty()) {
                        Text(
                            text = "No intruder captures recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                if (intruderCaptures.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.clearAllIntruderCaptures()
                            }
                        }
                    ) {
                        Text("Clear All", color = DestructiveRed)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntruderCapturesDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AdvancedSecurityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) WarningOrange.copy(alpha = 0.15f) else DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) WarningOrange else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) WarningOrange else TextSecondary
                )
            }
        }
        
        // Custom animated switch
        val thumbOffset by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
            label = "advancedSwitch"
        )
        
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) WarningOrange else DarkSurfaceVariant)
                .clickable { onToggle() }
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (24 * thumbOffset).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

private fun getDisguiseIcon(disguise: StealthManager.Disguise): ImageVector {
    return when (disguise) {
        StealthManager.Disguise.DEFAULT -> Icons.Default.Lock
        StealthManager.Disguise.CALCULATOR -> Icons.Default.Calculate
        StealthManager.Disguise.NOTES -> Icons.Default.Notes
        StealthManager.Disguise.UTILITIES -> Icons.Default.Settings
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
