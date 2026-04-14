package com.pionen.app.core.network

import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ProxyAwareHttpClient: Factory for creating OkHttp clients with proxy support.
 * 
 * Security Design:
 * - Routes traffic through Tor SOCKS5 proxy when enabled
 * - Prevents DNS leaks by using proxy for DNS resolution
 * - Falls back to direct connection when Tor disabled
 */
@Singleton
class ProxyAwareHttpClient @Inject constructor(
    private val torManager: TorManager
) {
    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 60L
        
        // Longer timeouts for Tor (slower network)
        private const val TOR_CONNECT_TIMEOUT_SECONDS = 60L
        private const val TOR_READ_TIMEOUT_SECONDS = 120L
        private const val TOR_WRITE_TIMEOUT_SECONDS = 120L
    }
    
    /**
     * Get OkHttp client configured based on current Tor status.
     * If Tor is enabled and connected, routes through SOCKS5 proxy.
     */
    fun getClient(): OkHttpClient {
        return if (torManager.isReady()) {
            createTorClient()
        } else {
            createDirectClient()
        }
    }
    
    /**
     * Create client that always uses Tor (for explicit Tor-only requests).
     */
    fun createTorClient(): OkHttpClient {
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", TorManager.SOCKS_PORT)
        )
        
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(TOR_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TOR_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TOR_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // DNS through proxy to prevent leaks
            .dns(TorDns())
            .build()
    }
    
    /**
     * Create direct client (no proxy).
     */
    fun createDirectClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    
    /**
     * Check if Tor is currently active.
     */
    fun isTorActive(): Boolean = torManager.isReady()
}

/**
 * Custom DNS resolver that routes through Tor to prevent DNS leaks.
 * When using SOCKS5 proxy, we want DNS resolution to happen on the proxy side.
 */
class TorDns : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> {
        // Return a placeholder address - actual resolution happens through SOCKS5
        // The proxy will resolve the hostname
        return listOf(java.net.InetAddress.getByName("0.0.0.0"))
    }
}
