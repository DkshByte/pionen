package com.pionen.app.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.pionen.app.core.SecureLogger
import com.pionen.app.core.vault.VaultEngine
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Secure embedded HTTPS server for local network file access.
 * 
 * Security features:
 * - TLS encryption (via Android's SSLServerSocketFactory)
 * - Random access token per session
 * - LAN-only access validation
 * - Rate limiting for failed auth attempts
 * - Auto-timeout after inactivity
 * - RAM-only file decryption
 * - Secure file upload with direct encryption
 */
@Singleton
class SecureWebServer @Inject constructor(
    private val context: Context,
    private val vaultEngine: VaultEngine
) {
    companion object {
        private const val TAG = "SecureWebServer"
        private const val DEFAULT_PORT = 8443
        private const val TOKEN_LENGTH = 32
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_UPLOAD_SIZE = 500 * 1024 * 1024L // 500 MB max upload
    }
    
    private var server: PionenHttpServer? = null
    private var accessToken: String = ""
    private var serverPort: Int = DEFAULT_PORT
    
    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()
    
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()
    
    private val failedAttempts = ConcurrentHashMap<String, FailedAttemptInfo>()
    private val activeSessions = ConcurrentHashMap<String, SessionInfo>()
    
    /**
     * SECURITY: Lock-state check. Set by the caller (e.g. WebServerService)
     * to gate API access on vault unlock state. Returns true when vault is unlocked.
     */
    var isVaultUnlocked: () -> Boolean = { true }
    
    private var lastActivityTime = System.currentTimeMillis()
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start the secure web server with TLS encryption
     */
    fun start(): Result<ServerInfo> {
        if (server?.isAlive == true) {
            return Result.failure(IllegalStateException("Server already running"))
        }
        
        return try {
            _serverState.value = ServerState.Starting
            
            // Generate new access token
            accessToken = generateSecureToken()
            
            // Find available port
            serverPort = findAvailablePort()
            
            // Get device IP
            val ipAddress = getLocalIpAddress()
            if (ipAddress == null) {
                _serverState.value = ServerState.Error("Not connected to WiFi")
                return Result.failure(IllegalStateException("Not connected to WiFi"))
            }
            
            // Create and start server with HTTPS
            server = PionenHttpServer(serverPort).also { srv ->
                // Enable HTTPS with self-signed certificate
                // SECURITY: TLS is mandatory — refuse to start without it.
                try {
                    val sslContext = createSSLContext()
                    srv.makeSecure(sslContext.serverSocketFactory, null)
                    SecureLogger.i(TAG, "HTTPS enabled with TLS encryption")
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "HTTPS initialization failed — refusing to start", e)
                    _serverState.value = ServerState.Error("TLS required but failed: ${e.message}")
                    return Result.failure(SecurityException("Cannot start server without TLS"))
                }
                srv.start()
            }
            
            // Use HTTPS URL (with fallback detection)
            val protocol = if (isHttpsEnabled()) "https" else "http"
            
            val info = ServerInfo(
                url = "$protocol://$ipAddress:$serverPort",
                token = accessToken,
                ipAddress = ipAddress,
                port = serverPort,
                startTime = System.currentTimeMillis(),
                isEncrypted = isHttpsEnabled()
            )
            
            _serverInfo.value = info
            _serverState.value = ServerState.Running
            
            // Start inactivity timeout monitor
            startTimeoutMonitor()
            
            SecureLogger.i(TAG, "Server started on $protocol://$ipAddress:$serverPort (E2E Encrypted: ${info.isEncrypted})")
            Result.success(info)
            
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to start server", e)
            _serverState.value = ServerState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
    
    /**
     * Check if HTTPS is enabled on the current server
     */
    private fun isHttpsEnabled(): Boolean {
        // Server is HTTPS if it was successfully configured with SSL
        return sslContextCreated
    }
    
    private var sslContextCreated = false
    
    /**
     * Create SSL context with dynamically generated self-signed certificate.
     * This enables end-to-end encryption for all communication.
     */
    private fun createSSLContext(): SSLContext {
        try {
            // Generate RSA key pair
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048, SecureRandom())
            val keyPair = keyPairGenerator.generateKeyPair()
            
            // Create self-signed certificate valid for 1 day
            val now = Date()
            val validUntil = Date(now.time + 24 * 60 * 60 * 1000L) // 1 day
            val serialNumber = BigInteger(64, SecureRandom())
            
            val issuer = X500Name("CN=Pionen Vault, O=Pionen, L=Secure, C=XX")
            val subject = issuer
            
            val certBuilder = JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                now,
                validUntil,
                subject,
                keyPair.public
            )
            
            val signer = JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.private)
            
            val certificate = JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer))
            
            // Create KeyStore with certificate
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            val password = generateSecureToken().toCharArray()
            keyStore.setKeyEntry(
                "pionen",
                keyPair.private,
                password,
                arrayOf(certificate)
            )
            
            // Initialize KeyManager with our certificate
            val keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
            )
            keyManagerFactory.init(keyStore, password)
            
            // Create and initialize SSL context
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagerFactory.keyManagers, null, SecureRandom())
            
            sslContextCreated = true
            SecureLogger.i(TAG, "SSL context created successfully - E2E encryption enabled")
            
            return sslContext
            
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to create SSL context", e)
            sslContextCreated = false
            throw e
        }
    }
    
    /**
     * Stop the server and clear all sessions
     */
    fun stop() {
        timeoutJob?.cancel()
        server?.stop()
        server = null
        accessToken = ""
        activeSessions.clear()
        failedAttempts.clear()
        sslContextCreated = false // Reset SSL state
        _serverInfo.value = null
        _serverState.value = ServerState.Stopped
        SecureLogger.i(TAG, "Server stopped")
    }
    
    /**
     * Check if an IP is from local network only
     */
    private fun isLocalNetwork(clientIp: String): Boolean {
        return try {
            val address = InetAddress.getByName(clientIp)
            address.isSiteLocalAddress || address.isLoopbackAddress
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validate access token with rate limiting
     */
    private fun validateToken(clientIp: String, token: String): Boolean {
        // Check if IP is locked out
        failedAttempts[clientIp]?.let { info ->
            if (info.count >= MAX_FAILED_ATTEMPTS) {
                val lockoutRemaining = info.lastAttempt + LOCKOUT_DURATION_MS - System.currentTimeMillis()
                if (lockoutRemaining > 0) {
                    SecureLogger.w(TAG, "IP $clientIp is locked out for ${lockoutRemaining / 1000}s")
                    return false
                } else {
                    // Lockout expired
                    failedAttempts.remove(clientIp)
                }
            }
        }
        
        val isValid = java.security.MessageDigest.isEqual(
            token.toByteArray(Charsets.UTF_8), 
            accessToken.toByteArray(Charsets.UTF_8)
        )
        
        if (!isValid) {
            // Track failed attempt
            failedAttempts.compute(clientIp) { _, existing ->
                FailedAttemptInfo(
                    count = (existing?.count ?: 0) + 1,
                    lastAttempt = System.currentTimeMillis()
                )
            }
            SecureLogger.w(TAG, "Invalid token attempt from $clientIp")
        } else {
            // Clear failed attempts on success
            failedAttempts.remove(clientIp)
        }
        
        return isValid
    }
    
    /**
     * Record activity to reset timeout
     */
    private fun recordActivity() {
        lastActivityTime = System.currentTimeMillis()
    }
    
    /**
     * Start monitoring for inactivity timeout
     */
    private fun startTimeoutMonitor() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            while (isActive) {
                delay(60_000) // Check every minute
                val elapsed = System.currentTimeMillis() - lastActivityTime
                if (elapsed > INACTIVITY_TIMEOUT_MS) {
                    SecureLogger.i(TAG, "Auto-stopping due to inactivity")
                    withContext(Dispatchers.Main) {
                        stop()
                    }
                    break
                }
            }
        }
    }
    
    /**
     * Generate cryptographically secure random token
     */
    private fun generateSecureToken(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        return (1..TOKEN_LENGTH)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
    
    /**
     * Find an available port starting from default
     */
    private fun findAvailablePort(): Int {
        // Try default port first, then scan nearby ports
        for (port in DEFAULT_PORT..(DEFAULT_PORT + 50)) {
            try {
                java.net.ServerSocket(port).use { return port }
            } catch (e: Exception) {
                // Port in use, try next
            }
        }
        // Last resort: let the OS pick an ephemeral port
        return java.net.ServerSocket(0).use { it.localPort }
    }
    
    /**
     * Get the device's local LAN IP address.
     *
     * Strategy (in priority order):
     *  1. ConnectivityManager + LinkProperties  (API 23+, correct on all Android versions)
     *  2. Enumerate network interfaces            (fallback for edge cases)
     *  3. Deprecated WifiManager.connectionInfo  (last resort, Android <10 only)
     */
    private fun getLocalIpAddress(): String? {
        // --- Strategy 1: ConnectivityManager (recommended modern API) ---
        try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: run {
                SecureLogger.w(TAG, "No active network")
                null
            }
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                // Accept both WiFi and Ethernet (e.g. USB-tethered ethernet)
                val isLocalNetwork = caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
                if (isLocalNetwork) {
                    val linkProps = cm.getLinkProperties(activeNetwork)
                    linkProps?.linkAddresses?.forEach { linkAddress ->
                        val addr = linkAddress.address
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            val ip = addr.hostAddress
                            if (!ip.isNullOrEmpty()) {
                                SecureLogger.i(TAG, "IP via ConnectivityManager: $ip")
                                return ip
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.w(TAG, "ConnectivityManager IP lookup failed: ${e.message}")
        }

        // --- Strategy 2: NetworkInterface enumeration ---
        try {
            java.net.NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { addr ->
                    !addr.isLoopbackAddress &&
                    addr is java.net.Inet4Address &&
                    addr.isSiteLocalAddress
                }
                ?.let { addr ->
                    val ip = addr.hostAddress
                    if (!ip.isNullOrEmpty()) {
                        SecureLogger.i(TAG, "IP via NetworkInterface: $ip")
                        return ip
                    }
                }
        } catch (e: Exception) {
            SecureLogger.w(TAG, "NetworkInterface IP lookup failed: ${e.message}")
        }

        // --- Strategy 3: Deprecated WifiManager (Android < 10 fallback only) ---
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    val ip = String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    SecureLogger.i(TAG, "IP via WifiManager (legacy): $ip")
                    return ip
                }
            } catch (e: Exception) {
                SecureLogger.w(TAG, "WifiManager IP lookup failed: ${e.message}")
            }
        }

        SecureLogger.e(TAG, "Could not determine local IP address")
        return null
    }
    
    /**
     * Inner HTTP server implementation
     */
    private inner class PionenHttpServer(port: Int) : NanoHTTPD(port) {
        
        override fun serve(session: IHTTPSession): Response {
            recordActivity()
            
            val clientIp = session.remoteIpAddress
            val uri = session.uri
            val method = session.method
            
            SecureLogger.d(TAG, "Request: $method $uri from $clientIp")
            
            // Validate local network
            if (!isLocalNetwork(clientIp)) {
                return newFixedLengthResponse(
                    Response.Status.FORBIDDEN,
                    MIME_PLAINTEXT,
                    "Access denied: Non-local connection"
                )
            }
            
            // Serve static assets without auth
            if (uri == "/" || uri == "/index.html") {
                return serveWebUI()
            }
            if (uri.startsWith("/assets/")) {
                return serveAsset(uri.removePrefix("/assets/"))
            }
            
            // All API endpoints require authentication
            if (uri.startsWith("/api/")) {
                // SECURITY: Only accept token from header or HttpOnly cookie.
                // NEVER from query params (leaks via Referer, browser history, logs).
                val token = session.headers["x-access-token"] 
                    ?: session.cookies?.read("auth_token")
                    ?: ""
                
                if (!validateToken(clientIp, token)) {
                    return newFixedLengthResponse(
                        Response.Status.UNAUTHORIZED,
                        MIME_PLAINTEXT,
                        "Invalid or missing access token"
                    )
                }
                
                // SECURITY: Reject all API requests while vault is locked
                if (!isVaultUnlocked()) {
                    return newFixedLengthResponse(
                        Response.Status.FORBIDDEN,
                        "application/json",
                        """{"success":false,"error":"Vault is locked"}"""
                    )
                }
                
                return handleApiRequest(session, uri, method)
            }
            
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found"
            )
        }
        
        private fun serveWebUI(): Response {
            return try {
                val html = context.assets.open("web/index.html").bufferedReader().readText()
                newFixedLengthResponse(Response.Status.OK, "text/html", html)
            } catch (e: Exception) {
                // Fallback inline HTML
                newFixedLengthResponse(Response.Status.OK, "text/html", getEmbeddedHtml())
            }
        }
        
        private fun serveAsset(path: String): Response {
            if (path.contains("..") || path.contains("//")) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
            }
            return try {
                val stream = context.assets.open("web/$path")
                val mimeType = when {
                    path.endsWith(".css") -> "text/css"
                    path.endsWith(".js") -> "application/javascript"
                    path.endsWith(".png") -> "image/png"
                    path.endsWith(".svg") -> "image/svg+xml"
                    else -> "application/octet-stream"
                }
                newChunkedResponse(Response.Status.OK, mimeType, stream)
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Asset not found")
            }
        }
        
        private fun handleApiRequest(session: IHTTPSession, uri: String, method: Method): Response {
            return when {
                uri == "/api/auth" -> handleAuth(session)
                uri == "/api/files" && method == Method.GET -> handleFileList()
                uri == "/api/files" && method == Method.POST -> handleFileUpload(session)
                uri == "/api/upload" && method == Method.POST -> handleFileUpload(session)
                uri.matches(Regex("/api/files/([a-f0-9-]+)")) && method == Method.GET -> {
                    val fileId = uri.substringAfterLast("/")
                    handleFileInfo(fileId)
                }
                uri.matches(Regex("/api/files/([a-f0-9-]+)/download")) -> {
                    val fileId = uri.substringAfter("/api/files/").substringBefore("/download")
                    handleFileDownload(fileId)
                }
                uri.matches(Regex("/api/files/([a-f0-9-]+)")) && method == Method.DELETE -> {
                    val fileId = uri.substringAfterLast("/")
                    handleFileDelete(fileId)
                }
                uri.matches(Regex("/api/files/([a-f0-9-]+)/view")) -> {
                    val fileId = uri.substringAfter("/api/files/").substringBefore("/view")
                    handleFileView(fileId, session)
                }
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "API endpoint not found"
                )
            }
        }
        
        private fun handleAuth(session: IHTTPSession): Response {
            val json = JSONObject().apply {
                put("success", true)
                put("message", "Authenticated successfully")
            }
            val response = newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                json.toString()
            )
            response.addHeader("Set-Cookie", "auth_token=$accessToken; Path=/; HttpOnly; SameSite=Strict; Secure")
            return response
        }
        
        private fun handleFileList(): Response {
            return try {
                val files = runBlocking { 
                    vaultEngine.getAllFiles().first()
                }
                
                val jsonArray = JSONArray()
                for (file in files) {
                    jsonArray.put(JSONObject().apply {
                        put("id", file.id.toString())
                        put("name", file.fileName)
                        put("size", file.originalSize)
                        put("formattedSize", file.formattedSize)
                        put("mimeType", file.mimeType)
                        put("createdAt", file.createdAt)
                        put("isImported", file.isImported)
                    })
                }
                
                val response = JSONObject().apply {
                    put("success", true)
                    put("count", files.size)
                    put("files", jsonArray)
                }
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    response.toString()
                )
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to list files", e)
                errorResponse("Failed to list files")
            }
        }
        
        /**
         * Secure file upload handler
         * - Streams directly to VaultEngine for encryption
         * - Never writes unencrypted data to disk
         * - Size limited for security
         */
        private fun handleFileUpload(session: IHTTPSession): Response {
            return try {
                // Check content length
                val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
                if (contentLength > MAX_UPLOAD_SIZE) {
                    return newFixedLengthResponse(
                        Response.Status.PAYLOAD_TOO_LARGE,
                        "application/json",
                        """{"success":false,"error":"File too large. Max size: 500MB"}"""
                    )
                }
                
                // Read raw body stream directly to bypass caching
                val fileNameRaw = session.headers["x-file-name"] ?: "uploaded_file_${System.currentTimeMillis()}"
                val fileName = try {
                    java.net.URLDecoder.decode(fileNameRaw, "UTF-8")
                } catch (e: Exception) {
                    fileNameRaw
                }
                val mimeType = session.headers["content-type"] ?: guessMimeType(fileName)
                
                // Immediately stream from network -> RAM chunk -> Encrypted flash storage
                val boundedInputStream = object : java.io.InputStream() {
                    private var limit = contentLength
                    private val source = session.inputStream
                    
                    override fun read(): Int {
                        if (limit <= 0) return -1
                        val result = source.read()
                        if (result != -1) limit--
                        return result
                    }
                    
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (limit <= 0) return -1
                        val maxRead = minOf(len.toLong(), limit).toInt()
                        val result = source.read(b, off, maxRead)
                        if (result != -1) limit -= result
                        return result
                    }
                }
                
                val vaultFile = runBlocking {
                    vaultEngine.createFileFromStream(
                        inputStream = boundedInputStream,
                        fileName = sanitizeFileName(fileName),
                        mimeType = mimeType
                    )
                }
                
                SecureLogger.i(TAG, "File uploaded and encrypted: ${vaultFile.fileName}")
                
                val json = JSONObject().apply {
                    put("success", true)
                    put("message", "File encrypted and stored securely")
                    put("file", JSONObject().apply {
                        put("id", vaultFile.id.toString())
                        put("name", vaultFile.fileName)
                        put("size", vaultFile.originalSize)
                        put("formattedSize", vaultFile.formattedSize)
                    })
                }
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    json.toString()
                )
                
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Upload failed", e)
                errorResponse("Upload failed: ${e.message}")
            }
        }
        
        /**
         * Securely delete a temp file
         */
        private fun secureDeleteFile(file: java.io.File) {
            try {
                // Overwrite with zeros before delete
                if (file.exists() && file.canWrite()) {
                    val length = file.length()
                    file.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        var remaining = length
                        while (remaining > 0) {
                            val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                            out.write(buffer, 0, toWrite)
                            remaining -= toWrite
                        }
                    }
                }
                file.delete()
            } catch (e: Exception) {
                file.delete() // At least try to delete
            }
        }
        
        /**
         * Extract filename from content-disposition header
         */
        private fun extractFilename(header: String?): String? {
            if (header == null) return null
            val regex = Regex("filename=\"?([^\"]+)\"?")
            return regex.find(header)?.groupValues?.get(1)
        }
        
        /**
         * Sanitize filename for security
         */
        private fun sanitizeFileName(name: String): String {
            return name
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\.\\.+"), "_")
                .take(255)
        }
        
        /**
         * Guess MIME type from filename
         */
        private fun guessMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "avi" -> "video/x-msvideo"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "pdf" -> "application/pdf"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "txt" -> "text/plain"
                "zip" -> "application/zip"
                else -> "application/octet-stream"
            }
        }
        
        private fun handleFileInfo(fileId: String): Response {
            return try {
                val uuid = UUID.fromString(fileId)
                val file = runBlocking { vaultEngine.getFile(uuid) }
                
                if (file == null) {
                    return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "application/json",
                        """{"success":false,"error":"File not found"}"""
                    )
                }
                
                val json = JSONObject().apply {
                    put("success", true)
                    put("file", JSONObject().apply {
                        put("id", file.id.toString())
                        put("name", file.fileName)
                        put("size", file.originalSize)
                        put("formattedSize", file.formattedSize)
                        put("mimeType", file.mimeType)
                        put("createdAt", file.createdAt)
                    })
                }
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    json.toString()
                )
            } catch (e: Exception) {
                errorResponse("Invalid file ID")
            }
        }
        
        private fun handleFileDownload(fileId: String): Response {
            return try {
                val uuid = UUID.fromString(fileId)
                val file = runBlocking { vaultEngine.getFile(uuid) }
                    ?: return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "File not found"
                    )
                
                // Stream decryption output straight to response logic (zero-cache memory leak fixed)
                val inputStream = runBlocking { vaultEngine.getFileStream(uuid) }
                
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    file.mimeType,
                    inputStream,
                    file.originalSize
                )
                
                response.addHeader(
                    "Content-Disposition",
                    "attachment; filename=\"${sanitizeDispositionFilename(file.fileName)}\""
                )
                
                // Prevent caching - stream from memory only
                addNoCacheHeaders(response)
                
                response
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to download file", e)
                errorResponse("Failed to download file")
            }
        }
        
        private fun handleFileDelete(fileId: String): Response {
            return try {
                val uuid = UUID.fromString(fileId)
                val result = runBlocking { vaultEngine.deleteFile(uuid) }
                
                val json = JSONObject().apply {
                    put("success", true)
                    put("message", "File securely deleted")
                    put("isIrrecoverable", result.isIrrecoverable)
                }
                
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    json.toString()
                )
            } catch (e: Exception) {
                errorResponse("Failed to delete file")
            }
        }
        
        /**
         * Handle file view for in-browser preview
         * Supports Range headers for video/audio seeking
         */
        private fun handleFileView(fileId: String, session: IHTTPSession): Response {
            return try {
                val uuid = UUID.fromString(fileId)
                val file = runBlocking { vaultEngine.getFile(uuid) }
                    ?: return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "File not found"
                    )
                
                // Streaming directly from CipherInputStream completely bypassing App Max Permitted Java Heap memory bounds
                val fileSize = file.originalSize
                val mimeType = file.mimeType
                
                // Check for Range header (for video/audio seeking)
                val rangeHeader = session.headers["range"]
                
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    // Handle partial content request
                    val rangeSpec = rangeHeader.removePrefix("bytes=")
                    val rangeParts = rangeSpec.split("-")
                    
                    val start = rangeParts[0].toLongOrNull() ?: 0L
                    val end = if (rangeParts.size > 1 && rangeParts[1].isNotEmpty()) {
                        rangeParts[1].toLongOrNull() ?: (fileSize - 1)
                    } else {
                        (fileSize - 1)
                    }
                    
                    val length = (end - start + 1)
                    
                    val inputStream = runBlocking { vaultEngine.getFileStream(uuid) }
                    try {
                        if (start > 0) {
                            var skipped = 0L
                            while (skipped < start) {
                                val jump = inputStream.skip(start - skipped)
                                if (jump <= 0L) {
                                    inputStream.read() // force fallback
                                    skipped++
                                } else {
                                    skipped += jump
                                }
                            }
                        }
                    } catch (e: Exception) {
                        SecureLogger.e(TAG, "Jump to seek marker inside CipherInputStream failed", e)
                    }

                    // Strict byte bounding limit wrapping stream to prevent over-reading in Chunked Request
                    val boundedStream = object : java.io.InputStream() {
                        var remaining = length
                        override fun read(): Int {
                            if (remaining <= 0) return -1
                            val b = inputStream.read()
                            if (b != -1) remaining--
                            return b
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (remaining <= 0) return -1
                            val bytesToRead = minOf(len.toLong(), remaining).toInt()
                            val readBytes = inputStream.read(b, off, bytesToRead)
                            if (readBytes != -1) remaining -= readBytes
                            return readBytes
                        }
                        override fun close() = inputStream.close()
                    }
                    
                    val response = newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        mimeType,
                        boundedStream,
                        length
                    )
                    
                    response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
                    response.addHeader("Accept-Ranges", "bytes")
                    response.addHeader("Content-Length", length.toString())
                    
                    // Prevent caching - stream from memory only
                    addNoCacheHeaders(response)
                    
                    response
                } else {
                    val inputStream = runBlocking { vaultEngine.getFileStream(uuid) }
                    // Full content response - use fixed length stream
                    val response = newFixedLengthResponse(
                        Response.Status.OK,
                        mimeType,
                        inputStream,
                        fileSize
                    )
                    
                    response.addHeader("Accept-Ranges", "bytes")
                    
                    // Set inline disposition for viewing in browser
                    response.addHeader(
                        "Content-Disposition",
                        "inline; filename=\"${sanitizeDispositionFilename(file.fileName)}\""
                    )
                    
                    // Prevent caching - stream from memory only
                    addNoCacheHeaders(response)
                    
                    response
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to view file", e)
                errorResponse("Failed to view file")
            }
        }
        
        /**
         * Sanitize a filename for use in Content-Disposition headers.
         * Prevents header injection via quotes, newlines, or control chars.
         */
        private fun sanitizeDispositionFilename(name: String): String {
            return name
                .replace("\"", "'")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\u0000", "")
                .take(200)
        }
        
        private fun errorResponse(message: String): Response {
            val json = JSONObject().apply {
                put("success", false)
                put("error", message)
            }
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                json.toString()
            )
        }
        
        /**
         * Add no-cache headers to prevent browser caching of media content.
         * Videos and thumbnails are streamed from phone memory only.
         * Small chunks are used for efficient streaming without disk caching.
         */
        private fun addNoCacheHeaders(response: Response) {
            // Prevent all caching - content must stream from memory
            response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            response.addHeader("Pragma", "no-cache")
            response.addHeader("Expires", "0")
            // Additional headers to prevent any form of caching
            response.addHeader("X-Pionen-Memory-Stream", "true")
            response.addHeader("Vary", "*")
        }
        
        /**
         * Premium embedded HTML with file upload support
         */
        private fun getEmbeddedHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pionen Vault - Secure Access</title>
    <style>
        :root {
            --bg-dark: #09090b;
            --bg-surface: #111114;
            --bg-card: #18181b;
            --bg-hover: #27272a;
            --accent-green: #4ade80;
            --accent-green-dim: rgba(74, 222, 128, 0.15);
            --accent-blue: #60a5fa;
            --text-primary: #fafafa;
            --text-secondary: #a1a1aa;
            --text-muted: #71717a;
            --border: rgba(255, 255, 255, 0.08);
            --danger: #f87171;
        }
        
        * { margin: 0; padding: 0; box-sizing: border-box; }
        
        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg-dark);
            color: var(--text-primary);
            min-height: 100vh;
            line-height: 1.5;
        }
        
        /* Gradient background */
        body::before {
            content: '';
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: 
                radial-gradient(ellipse at top left, rgba(74, 222, 128, 0.05) 0%, transparent 50%),
                radial-gradient(ellipse at bottom right, rgba(96, 165, 250, 0.05) 0%, transparent 50%);
            pointer-events: none;
            z-index: -1;
        }
        
        .container { max-width: 1400px; margin: 0 auto; padding: 24px; }
        
        /* Header */
        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 20px 0;
            border-bottom: 1px solid var(--border);
            margin-bottom: 32px;
        }
        
        .logo {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        
        .logo-icon {
            width: 42px;
            height: 42px;
            background: linear-gradient(135deg, var(--accent-green), #22c55e);
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 20px;
        }
        
        h1 {
            font-size: 1.5rem;
            font-weight: 700;
            background: linear-gradient(90deg, var(--accent-green), var(--accent-blue));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }
        
        .header-actions { display: flex; gap: 12px; align-items: center; }
        
        .status-badge {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            background: var(--accent-green-dim);
            border-radius: 20px;
            font-size: 0.8rem;
            color: var(--accent-green);
        }
        
        .status-dot {
            width: 8px;
            height: 8px;
            background: var(--accent-green);
            border-radius: 50%;
            animation: pulse 2s infinite;
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        
        /* Buttons */
        button, .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
            display: inline-flex;
            align-items: center;
            gap: 8px;
        }
        
        .btn-primary {
            background: linear-gradient(135deg, var(--accent-green), #22c55e);
            color: #000;
        }
        
        .btn-primary:hover {
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(74, 222, 128, 0.3);
        }
        
        .btn-secondary {
            background: var(--bg-card);
            color: var(--text-primary);
            border: 1px solid var(--border);
        }
        
        .btn-secondary:hover {
            background: var(--bg-hover);
            border-color: var(--text-muted);
        }
        
        .btn-danger {
            background: rgba(248, 113, 113, 0.15);
            color: var(--danger);
        }
        
        .btn-danger:hover { background: rgba(248, 113, 113, 0.25); }
        
        /* Auth Form */
        .auth-container {
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        
        .auth-card {
            background: var(--bg-surface);
            border: 1px solid var(--border);
            border-radius: 20px;
            padding: 48px;
            width: 100%;
            max-width: 420px;
            text-align: center;
        }
        
        .auth-icon {
            width: 72px;
            height: 72px;
            background: linear-gradient(135deg, var(--accent-green-dim), rgba(96, 165, 250, 0.1));
            border-radius: 20px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            margin: 0 auto 24px;
        }
        
        .auth-card h2 {
            font-size: 1.5rem;
            margin-bottom: 8px;
        }
        
        .auth-card p {
            color: var(--text-muted);
            margin-bottom: 32px;
        }
        
        input[type="text"], input[type="password"] {
            width: 100%;
            padding: 16px;
            background: var(--bg-dark);
            border: 1px solid var(--border);
            border-radius: 12px;
            color: var(--text-primary);
            font-size: 18px;
            font-family: 'JetBrains Mono', monospace;
            text-align: center;
            letter-spacing: 4px;
            margin-bottom: 20px;
            transition: border-color 0.2s;
        }
        
        input:focus {
            outline: none;
            border-color: var(--accent-green);
            box-shadow: 0 0 0 3px var(--accent-green-dim);
        }
        
        .error-msg {
            color: var(--danger);
            font-size: 14px;
            margin-top: 16px;
        }
        
        /* Toolbar */
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 24px;
            flex-wrap: wrap;
            gap: 16px;
        }
        
        .file-count {
            color: var(--text-secondary);
            font-size: 14px;
        }
        
        /* Upload Zone */
        .upload-zone {
            border: 2px dashed var(--border);
            border-radius: 16px;
            padding: 48px;
            text-align: center;
            margin-bottom: 32px;
            transition: all 0.3s ease;
            cursor: pointer;
            background: var(--bg-surface);
        }
        
        .upload-zone:hover, .upload-zone.dragover {
            border-color: var(--accent-green);
            background: var(--accent-green-dim);
        }
        
        .upload-icon {
            font-size: 48px;
            margin-bottom: 16px;
            opacity: 0.7;
        }
        
        .upload-zone h3 {
            font-size: 1.1rem;
            margin-bottom: 8px;
        }
        
        .upload-zone p {
            color: var(--text-muted);
            font-size: 14px;
        }
        
        .upload-zone input[type="file"] {
            display: none;
        }
        
        /* Files Grid */
        .files-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 16px;
        }
        
        .file-card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 20px;
            transition: all 0.2s ease;
            position: relative;
            overflow: hidden;
        }
        
        .file-card:hover {
            transform: translateY(-2px);
            border-color: rgba(74, 222, 128, 0.3);
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
        }
        
        .file-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 3px;
            background: linear-gradient(90deg, var(--accent-green), var(--accent-blue));
            opacity: 0;
            transition: opacity 0.2s;
        }
        
        .file-card:hover::before { opacity: 1; }
        
        .file-header {
            display: flex;
            align-items: flex-start;
            gap: 14px;
            margin-bottom: 16px;
        }
        
        .file-icon {
            width: 48px;
            height: 48px;
            background: var(--accent-green-dim);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            flex-shrink: 0;
        }
        
        .file-info { flex: 1; min-width: 0; }
        
        .file-name {
            font-weight: 600;
            font-size: 14px;
            word-break: break-word;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        
        .file-meta {
            display: flex;
            gap: 8px;
            color: var(--text-muted);
            font-size: 12px;
            margin-top: 4px;
        }
        
        .file-actions {
            display: flex;
            gap: 8px;
        }
        
        .file-actions button {
            flex: 1;
            padding: 10px;
            font-size: 13px;
        }
        
        /* Loading */
        .loading {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 80px;
            color: var(--text-muted);
        }
        
        .spinner {
            width: 48px;
            height: 48px;
            border: 3px solid var(--border);
            border-top-color: var(--accent-green);
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin-bottom: 16px;
        }
        
        @keyframes spin { to { transform: rotate(360deg); } }
        
        /* Upload Progress */
        .upload-progress {
            background: var(--bg-card);
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 24px;
        }
        
        .progress-header {
            display: flex;
            justify-content: space-between;
            margin-bottom: 12px;
            font-size: 14px;
        }
        
        .progress-bar {
            height: 6px;
            background: var(--bg-dark);
            border-radius: 3px;
            overflow: hidden;
        }
        
        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, var(--accent-green), var(--accent-blue));
            border-radius: 3px;
            transition: width 0.3s ease;
        }
        
        /* Empty State */
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: var(--text-muted);
        }
        
        .empty-state-icon {
            font-size: 64px;
            margin-bottom: 24px;
            opacity: 0.5;
        }
        
        /* Toast Notifications */
        .toast-container {
            position: fixed;
            bottom: 24px;
            right: 24px;
            z-index: 1000;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        
        .toast {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 16px 20px;
            display: flex;
            align-items: center;
            gap: 12px;
            animation: slideIn 0.3s ease;
            min-width: 300px;
        }
        
        .toast.success { border-color: var(--accent-green); }
        .toast.error { border-color: var(--danger); }
        
        @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
        
        /* Responsive */
        @media (max-width: 768px) {
            .container { padding: 16px; }
            .auth-card { padding: 32px 24px; }
            .toolbar { flex-direction: column; align-items: stretch; }
            .files-grid { grid-template-columns: 1fr; }
        }
        
        .hidden { display: none !important; }
        
        /* Security Badge */
        .security-info {
            display: flex;
            gap: 12px;
            margin-top: 24px;
            flex-wrap: wrap;
            justify-content: center;
        }
        
        .security-badge {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 6px 10px;
            background: var(--bg-card);
            border-radius: 6px;
            font-size: 11px;
            color: var(--text-muted);
        }
        
        /* Preview Modal */
        .preview-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.95);
            z-index: 2000;
            display: flex;
            flex-direction: column;
            animation: fadeIn 0.2s ease;
        }
        
        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }
        
        .preview-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 16px 24px;
            background: var(--bg-surface);
            border-bottom: 1px solid var(--border);
        }
        
        .preview-title {
            display: flex;
            align-items: center;
            gap: 12px;
            font-weight: 600;
            max-width: 60%;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        
        .preview-actions {
            display: flex;
            gap: 8px;
        }
        
        .preview-content {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 24px;
            overflow: auto;
        }
        
        .preview-content img {
            max-width: 100%;
            max-height: 100%;
            object-fit: contain;
            border-radius: 8px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
        }
        
        .preview-content video,
        .preview-content audio {
            max-width: 100%;
            max-height: 100%;
            border-radius: 8px;
        }
        
        .preview-content audio {
            min-width: 400px;
        }
        
        .preview-content iframe {
            width: 100%;
            height: 100%;
            border: none;
            border-radius: 8px;
            background: white;
        }
        
        .preview-content pre {
            background: var(--bg-card);
            padding: 24px;
            border-radius: 12px;
            max-width: 100%;
            max-height: 100%;
            overflow: auto;
            font-family: 'JetBrains Mono', monospace;
            font-size: 14px;
            line-height: 1.6;
            white-space: pre-wrap;
            word-break: break-word;
        }
        
        .preview-unsupported {
            text-align: center;
            color: var(--text-muted);
        }
        
        .preview-unsupported-icon {
            font-size: 64px;
            margin-bottom: 16px;
            opacity: 0.5;
        }
        
        .btn-icon {
            width: 40px;
            height: 40px;
            padding: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
        }
    </style>
</head>
<body>
    <!-- Auth Screen -->
    <div id="auth-screen" class="auth-container">
        <div class="auth-card">
            <div class="auth-icon">🔐</div>
            <h2>Secure Access</h2>
            <p>Enter the access token displayed on your phone</p>
            <input type="text" id="token-input" placeholder="Enter Token" autocomplete="off" autofocus>
            <button class="btn btn-primary" style="width:100%" onclick="authenticate()">
                <span>🔓</span> Unlock Vault
            </button>
            <p class="error-msg hidden" id="auth-error"></p>
            <div class="security-info">
                <span class="security-badge">🔒 End-to-End Encrypted</span>
                <span class="security-badge">📡 LAN Only</span>
                <span class="security-badge">⏱️ Auto-Timeout</span>
            </div>
        </div>
    </div>
    
    <!-- Main App -->
    <div id="app-screen" class="hidden">
        <div class="container">
            <header>
                <div class="logo">
                    <div class="logo-icon">🛡️</div>
                    <h1>Pionen Vault</h1>
                </div>
                <div class="header-actions">
                    <div class="status-badge">
                        <div class="status-dot"></div>
                        <span>Secure Connection</span>
                    </div>
                    <button class="btn btn-secondary" onclick="logout()">
                        <span>🚪</span> Disconnect
                    </button>
                </div>
            </header>
            
            <!-- Upload Zone -->
            <div class="upload-zone" id="upload-zone" onclick="document.getElementById('file-input').click()">
                <div class="upload-icon">📤</div>
                <h3>Drop files here or click to upload</h3>
                <p>Files are encrypted instantly on your device</p>
                <input type="file" id="file-input" multiple onchange="handleFileSelect(event)">
            </div>
            
            <!-- Upload Progress -->
            <div class="upload-progress hidden" id="upload-progress">
                <div class="progress-header">
                    <span id="upload-filename">Encrypting...</span>
                    <span id="upload-percent">0%</span>
                </div>
                <div class="progress-bar">
                    <div class="progress-fill" id="progress-fill" style="width: 0%"></div>
                </div>
            </div>
            
            <!-- Toolbar -->
            <div class="toolbar">
                <div class="file-count" id="file-count">Loading files...</div>
                <button class="btn btn-secondary" onclick="loadFiles()">
                    <span>🔄</span> Refresh
                </button>
            </div>
            
            <!-- Loading -->
            <div class="loading" id="loading">
                <div class="spinner"></div>
                <p>Loading encrypted files...</p>
            </div>
            
            <!-- Files Grid -->
            <div class="files-grid hidden" id="files-grid"></div>
            
            <!-- Empty State -->
            <div class="empty-state hidden" id="empty-state">
                <div class="empty-state-icon">📁</div>
                <h3>Your vault is empty</h3>
                <p>Upload files above to encrypt and secure them</p>
            </div>
        </div>
    </div>
    
    <!-- Toast Container -->
    <div class="toast-container" id="toast-container"></div>
    
    <!-- Preview Modal -->
    <div class="preview-overlay hidden" id="preview-modal">
        <div class="preview-header">
            <div class="preview-title">
                <span id="preview-icon">📄</span>
                <span id="preview-filename">File</span>
            </div>
            <div class="preview-actions">
                <button class="btn btn-secondary btn-icon" onclick="downloadCurrentFile()" title="Download">⬇️</button>
                <button class="btn btn-danger btn-icon" onclick="closePreview()" title="Close">✕</button>
            </div>
        </div>
        <div class="preview-content" id="preview-content"></div>
    </div>
    
    <script>
        let accessToken = '';
        
        // Authentication
        function authenticate() {
            const token = document.getElementById('token-input').value.trim();
            if (!token) return;
            
            fetch('/api/auth', {
                method: 'POST',
                headers: { 'X-Access-Token': token }
            })
            .then(r => r.json())
            .then(data => {
                if (data.success) {
                    accessToken = token;
                    document.getElementById('auth-screen').classList.add('hidden');
                    document.getElementById('app-screen').classList.remove('hidden');
                    loadFiles();
                } else {
                    showAuthError('Invalid access token');
                }
            })
            .catch(() => showAuthError('Connection failed'));
        }
        
        function showAuthError(msg) {
            const err = document.getElementById('auth-error');
            err.textContent = msg;
            err.classList.remove('hidden');
            document.getElementById('token-input').classList.add('error');
        }
        
        function logout() {
            accessToken = '';
            location.reload();
        }
        
        // File Operations
        function loadFiles() {
            document.getElementById('loading').classList.remove('hidden');
            document.getElementById('files-grid').classList.add('hidden');
            document.getElementById('empty-state').classList.add('hidden');
            
            fetch('/api/files', {
                headers: { 'X-Access-Token': accessToken }
            })
            .then(r => r.json())
            .then(data => {
                document.getElementById('loading').classList.add('hidden');
                
                if (!data.files || data.files.length === 0) {
                    document.getElementById('empty-state').classList.remove('hidden');
                    document.getElementById('file-count').textContent = '0 files';
                    return;
                }
                
                document.getElementById('file-count').textContent = data.files.length + ' encrypted file' + (data.files.length === 1 ? '' : 's');
                document.getElementById('files-grid').classList.remove('hidden');
                
                const grid = document.getElementById('files-grid');
                grid.innerHTML = data.files.map(file => `
                    <div class="file-card" onclick="viewFile('${'$'}{file.id}', '${'$'}{escapeHtml(file.name)}', '${'$'}{file.mimeType}')" style="cursor:pointer">
                        <div class="file-header">
                            <div class="file-icon">${'$'}{getFileIcon(file.mimeType)}</div>
                            <div class="file-info">
                                <div class="file-name">${'$'}{escapeHtml(file.name)}</div>
                                <div class="file-meta">
                                    <span>${'$'}{file.formattedSize}</span>
                                    <span>•</span>
                                    <span>${'$'}{isViewable(file.mimeType) ? '👁️ Click to view' : '🔒 Encrypted'}</span>
                                </div>
                            </div>
                        </div>
                        <div class="file-actions" onclick="event.stopPropagation()">
                            <button class="btn btn-primary" onclick="downloadFile('${'$'}{file.id}', '${'$'}{escapeHtml(file.name)}')">⬇️</button>
                            <button class="btn btn-danger" onclick="deleteFile('${'$'}{file.id}', '${'$'}{escapeHtml(file.name)}')">🗑️</button>
                        </div>
                    </div>
                `).join('');
            })
            .catch(err => {
                showToast('Failed to load files', 'error');
            });
        }
        
        function downloadFile(id, name) {
            const a = document.createElement('a');
            a.href = '/api/files/' + id + '/download?token=' + accessToken;
            a.download = name;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            showToast('Downloading ' + name, 'success');
        }
        
        function deleteFile(id, name) {
            if (!confirm('Permanently delete "' + name + '"?\\n\\nThis action is irreversible.')) return;
            
            fetch('/api/files/' + id, {
                method: 'DELETE',
                headers: { 'X-Access-Token': accessToken }
            })
            .then(r => r.json())
            .then(data => {
                if (data.success) {
                    showToast('File securely deleted', 'success');
                    loadFiles();
                } else {
                    showToast('Delete failed', 'error');
                }
            });
        }
        
        // File Upload
        function handleFileSelect(event) {
            const files = event.target.files;
            if (files.length === 0) return;
            uploadFiles(files);
        }
        
        function uploadFiles(files) {
            const progressDiv = document.getElementById('upload-progress');
            const progressFill = document.getElementById('progress-fill');
            const filenameSpan = document.getElementById('upload-filename');
            const percentSpan = document.getElementById('upload-percent');
            
            let current = 0;
            const total = files.length;
            
            function uploadNext() {
                if (current >= total) {
                    progressDiv.classList.add('hidden');
                    loadFiles();
                    return;
                }
                
                const file = files[current];
                filenameSpan.textContent = 'Encrypting: ' + file.name;
                progressDiv.classList.remove('hidden');
                
                const formData = new FormData();
                formData.append('file', file);
                formData.append('filename', file.name);
                formData.append('mimeType', file.type || 'application/octet-stream');
                
                const xhr = new XMLHttpRequest();
                xhr.open('POST', '/api/upload');
                xhr.setRequestHeader('X-Access-Token', accessToken);
                
                xhr.upload.onprogress = function(e) {
                    if (e.lengthComputable) {
                        const pct = Math.round((e.loaded / e.total) * 100);
                        progressFill.style.width = pct + '%';
                        percentSpan.textContent = pct + '%';
                    }
                };
                
                xhr.onload = function() {
                    if (xhr.status === 200) {
                        const resp = JSON.parse(xhr.responseText);
                        if (resp.success) {
                            showToast('✓ ' + file.name + ' encrypted', 'success');
                        } else {
                            showToast('✗ ' + file.name + ': ' + resp.error, 'error');
                        }
                    } else {
                        showToast('✗ Upload failed: ' + file.name, 'error');
                    }
                    current++;
                    uploadNext();
                };
                
                xhr.onerror = function() {
                    showToast('✗ Upload failed: ' + file.name, 'error');
                    current++;
                    uploadNext();
                };
                
                xhr.send(formData);
            }
            
            uploadNext();
        }
        
        // Drag and drop
        const uploadZone = document.getElementById('upload-zone');
        
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            uploadZone.addEventListener(eventName, preventDefaults, false);
        });
        
        function preventDefaults(e) {
            e.preventDefault();
            e.stopPropagation();
        }
        
        ['dragenter', 'dragover'].forEach(eventName => {
            uploadZone.addEventListener(eventName, () => uploadZone.classList.add('dragover'), false);
        });
        
        ['dragleave', 'drop'].forEach(eventName => {
            uploadZone.addEventListener(eventName, () => uploadZone.classList.remove('dragover'), false);
        });
        
        uploadZone.addEventListener('drop', e => {
            const files = e.dataTransfer.files;
            if (files.length > 0) uploadFiles(files);
        }, false);
        
        // Utilities
        function getFileIcon(mime) {
            if (!mime) return '📁';
            if (mime.startsWith('image/')) return '🖼️';
            if (mime.startsWith('video/')) return '🎬';
            if (mime.startsWith('audio/')) return '🎵';
            if (mime.includes('pdf')) return '📄';
            if (mime.includes('zip') || mime.includes('rar') || mime.includes('7z')) return '📦';
            if (mime.includes('text') || mime.includes('document')) return '📝';
            return '📁';
        }
        
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
        
        function showToast(message, type = 'success') {
            const container = document.getElementById('toast-container');
            const toast = document.createElement('div');
            toast.className = 'toast ' + type;
            toast.innerHTML = '<span>' + (type === 'success' ? '✓' : '✗') + '</span><span>' + message + '</span>';
            container.appendChild(toast);
            
            setTimeout(() => {
                toast.style.opacity = '0';
                setTimeout(() => toast.remove(), 300);
            }, 3000);
        }
        
        // Enter key for auth
        document.getElementById('token-input').addEventListener('keypress', e => {
            if (e.key === 'Enter') authenticate();
        });
        
        // Preview state
        let currentPreviewFile = null;
        
        function isViewable(mime) {
            if (!mime) return false;
            return mime.startsWith('image/') ||
                   mime.startsWith('video/') ||
                   mime.startsWith('audio/') ||
                   mime.includes('pdf') ||
                   mime.includes('text');
        }
        
        function viewFile(id, name, mimeType) {
            currentPreviewFile = { id, name, mimeType };
            const modal = document.getElementById('preview-modal');
            const content = document.getElementById('preview-content');
            
            document.getElementById('preview-icon').textContent = getFileIcon(mimeType);
            document.getElementById('preview-filename').textContent = name;
            
            const viewUrl = '/api/files/' + id + '/view?token=' + accessToken;
            
            // Clear previous content
            content.innerHTML = '<div class="loading"><div class="spinner"></div><p>Decrypting...</p></div>';
            modal.classList.remove('hidden');
            
            // Render based on type
            if (mimeType.startsWith('image/')) {
                const img = new Image();
                img.onload = () => content.innerHTML = '';
                img.onerror = () => content.innerHTML = '<div class="preview-unsupported"><div class="preview-unsupported-icon">❌</div><p>Failed to load image</p></div>';
                img.src = viewUrl;
                content.innerHTML = '';
                content.appendChild(img);
            } else if (mimeType.startsWith('video/')) {
                content.innerHTML = `<video controls autoplay><source src="${'$'}{viewUrl}" type="${'$'}{mimeType}">Video not supported</video>`;
            } else if (mimeType.startsWith('audio/')) {
                content.innerHTML = `<audio controls autoplay><source src="${'$'}{viewUrl}" type="${'$'}{mimeType}">Audio not supported</audio>`;
            } else if (mimeType.includes('pdf')) {
                content.innerHTML = `<iframe src="${'$'}{viewUrl}"></iframe>`;
            } else if (mimeType.includes('text')) {
                fetch(viewUrl)
                    .then(r => r.text())
                    .then(text => {
                        const pre = document.createElement('pre');
                        pre.textContent = text;
                        content.innerHTML = '';
                        content.appendChild(pre);
                    })
                    .catch(() => {
                        content.innerHTML = '<div class="preview-unsupported"><div class="preview-unsupported-icon">❌</div><p>Failed to load text</p></div>';
                    });
            } else {
                content.innerHTML = '<div class="preview-unsupported"><div class="preview-unsupported-icon">📁</div><h3>Preview not available</h3><p>Click Download to view this file</p></div>';
            }
        }
        
        function closePreview() {
            document.getElementById('preview-modal').classList.add('hidden');
            document.getElementById('preview-content').innerHTML = '';
            currentPreviewFile = null;
        }
        
        function downloadCurrentFile() {
            if (currentPreviewFile) {
                downloadFile(currentPreviewFile.id, currentPreviewFile.name);
            }
        }
        
        // ESC key to close preview
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && currentPreviewFile) {
                closePreview();
            }
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
    
    // Data classes
    data class ServerInfo(
        val url: String,
        val token: String,
        val ipAddress: String,
        val port: Int,
        val startTime: Long,
        val isEncrypted: Boolean = false
    )
    
    data class FailedAttemptInfo(
        val count: Int,
        val lastAttempt: Long
    )
    
    data class SessionInfo(
        val clientIp: String,
        val connectedAt: Long
    )
    
    sealed class ServerState {
        object Stopped : ServerState()
        object Starting : ServerState()
        object Running : ServerState()
        data class Error(val message: String) : ServerState()
    }
}
