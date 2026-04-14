package com.pionen.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pionen.app.core.network.TorConnectionState
import com.pionen.app.ui.theme.*

/**
 * Settings card for Tor configuration with animated status indicators.
 */
@Composable
fun TorSettingsCard(
    connectionState: TorConnectionState,
    bootstrapProgress: Int,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tor_pulse")
    
    // Pulsing animation for connecting state
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Rotation for connecting indicator
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tor icon with status indicator
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        AccentPurple.copy(alpha = 0.3f),
                                        AccentPurple.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Tor",
                            tint = AccentPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            "Tor Network",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            getStatusText(connectionState),
                            style = MaterialTheme.typography.bodySmall,
                            color = getStatusColor(connectionState)
                        )
                    }
                }
                
                // Toggle switch
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentPurple,
                        checkedTrackColor = AccentPurple.copy(alpha = 0.5f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
            
            // Connection progress (shown when connecting)
            if (connectionState is TorConnectionState.Connecting) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Establishing circuit...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(rotation)
                            )
                            Text(
                                "$bootstrapProgress%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentPurple
                            )
                        }
                    }
                    
                    LinearProgressIndicator(
                        progress = bootstrapProgress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentPurple,
                        trackColor = DarkSurfaceVariant
                    )
                }
            }
            
            // Connected status
            if (connectionState is TorConnectionState.Connected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(VaultGreenSubtle.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VaultGreen)
                    )
                    Text(
                        "Browsing anonymously via Tor",
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultGreen
                    )
                }
            }
            
            // Error state
            if (connectionState is TorConnectionState.Error) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DestructiveRed.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = DestructiveRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        connectionState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = DestructiveRed
                    )
                }
            }
            
            // Info text
            Text(
                "Routes browser traffic through the Tor network for anonymity. May be slower than direct connection.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

private fun getStatusText(state: TorConnectionState): String = when (state) {
    is TorConnectionState.Disconnected -> "Not connected"
    is TorConnectionState.Connecting -> "Connecting..."
    is TorConnectionState.Connected -> "Connected"
    is TorConnectionState.Error -> "Error"
}

private fun getStatusColor(state: TorConnectionState): Color = when (state) {
    is TorConnectionState.Disconnected -> TextMuted
    is TorConnectionState.Connecting -> AccentPurple
    is TorConnectionState.Connected -> VaultGreen
    is TorConnectionState.Error -> DestructiveRed
}
