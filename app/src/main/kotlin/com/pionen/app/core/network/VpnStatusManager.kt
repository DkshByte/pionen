package com.pionen.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VpnStatusManager: Monitors system VPN connection status.
 * 
 * Security Design:
 * - Detects if system VPN is active
 * - Provides real-time VPN status updates
 * - Does not manage VPN connections (user's VPN app does that)
 */
@Singleton
class VpnStatusManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as ConnectivityManager
    
    private val _vpnStatus = MutableStateFlow(checkVpnStatus())
    val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    init {
        startMonitoring()
    }
    
    /**
     * Start monitoring VPN status changes.
     */
    private fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _vpnStatus.value = VpnStatus.Connected(getVpnInfo())
            }
            
            override fun onLost(network: Network) {
                // Re-check in case there are multiple VPN networks
                _vpnStatus.value = checkVpnStatus()
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    _vpnStatus.value = VpnStatus.Connected(getVpnInfo())
                }
            }
        }
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            // Fallback to polling if callback fails
            _vpnStatus.value = checkVpnStatus()
        }
    }
    
    /**
     * Check current VPN status.
     */
    fun checkVpnStatus(): VpnStatus {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { 
                connectivityManager.getNetworkCapabilities(it) 
            }
            
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                VpnStatus.Connected(getVpnInfo())
            } else {
                // Check all networks for VPN
                val networks = connectivityManager.allNetworks
                val hasVpn = networks.any { network ->
                    connectivityManager.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
                
                if (hasVpn) {
                    VpnStatus.Connected(getVpnInfo())
                } else {
                    VpnStatus.Disconnected
                }
            }
        } catch (e: Exception) {
            VpnStatus.Unknown
        }
    }
    
    /**
     * Refresh VPN status manually.
     */
    fun refresh() {
        _vpnStatus.value = checkVpnStatus()
    }
    
    /**
     * Get VPN info (name is not always available on Android).
     */
    private fun getVpnInfo(): VpnInfo {
        return VpnInfo(
            name = null, // VPN name not exposed by Android API
            isActive = true
        )
    }
    
    /**
     * Check if VPN is currently active.
     */
    fun isVpnActive(): Boolean = _vpnStatus.value is VpnStatus.Connected
    
    fun cleanup() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        networkCallback = null
    }
}

/**
 * VPN connection status states.
 */
sealed class VpnStatus {
    object Disconnected : VpnStatus()
    data class Connected(val info: VpnInfo) : VpnStatus()
    object Unknown : VpnStatus()
}

/**
 * VPN connection information.
 */
data class VpnInfo(
    val name: String?,
    val isActive: Boolean
)
