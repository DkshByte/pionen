package com.pionen.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pionen.app.core.network.VpnStatus
import com.pionen.app.ui.theme.*

/**
 * Settings card showing VPN connection status.
 */
@Composable
fun VpnStatusCard(
    vpnStatus: VpnStatus,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
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
                    // VPN icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        SecureBlue.copy(alpha = 0.3f),
                                        SecureBlue.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "VPN",
                            tint = SecureBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            "VPN Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            getVpnStatusText(vpnStatus),
                            style = MaterialTheme.typography.bodySmall,
                            color = getVpnStatusColor(vpnStatus)
                        )
                    }
                }
                
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(getVpnStatusColor(vpnStatus))
                )
            }
            
            // Status card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (vpnStatus) {
                            is VpnStatus.Connected -> VaultGreenSubtle.copy(alpha = 0.1f)
                            else -> DarkSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (vpnStatus) {
                            is VpnStatus.Connected -> Icons.Default.Shield
                            is VpnStatus.Disconnected -> Icons.Default.ShieldMoon
                            is VpnStatus.Unknown -> Icons.Default.Help
                        },
                        contentDescription = null,
                        tint = getVpnStatusColor(vpnStatus),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        getVpnDescription(vpnStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Open VPN settings button
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to general settings
                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SecureBlue
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(SecureBlue.copy(alpha = 0.5f), SecureBlue.copy(alpha = 0.3f))
                    )
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open VPN Settings")
            }
            
            // Info text
            Text(
                "VPN is managed by external apps. Connect to your preferred VPN for enhanced privacy.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

private fun getVpnStatusText(status: VpnStatus): String = when (status) {
    is VpnStatus.Connected -> "Connected"
    is VpnStatus.Disconnected -> "Not connected"
    is VpnStatus.Unknown -> "Unknown"
}

private fun getVpnStatusColor(status: VpnStatus): androidx.compose.ui.graphics.Color = when (status) {
    is VpnStatus.Connected -> VaultGreen
    is VpnStatus.Disconnected -> TextMuted
    is VpnStatus.Unknown -> TextSecondary
}

private fun getVpnDescription(status: VpnStatus): String = when (status) {
    is VpnStatus.Connected -> "Your traffic is protected by VPN"
    is VpnStatus.Disconnected -> "No VPN connection detected"
    is VpnStatus.Unknown -> "Unable to determine VPN status"
}
