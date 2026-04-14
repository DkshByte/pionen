package com.pionen.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.pionen.app.ui.theme.*
import com.pionen.app.ui.viewmodels.SecureBrowserViewModel
import kotlinx.coroutines.launch

/**
 * Premium Secure Browser with refined incognito styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SecureBrowserScreen(
    onBack: () -> Unit,
    viewModel: SecureBrowserViewModel = hiltViewModel()
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        var url by remember { mutableStateOf("https://www.google.com") }
        var currentUrl by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var canGoBack by remember { mutableStateOf(false) }
        var canGoForward by remember { mutableStateOf(false) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        
        val downloadProgress by viewModel.downloadProgress.collectAsState()
        val downloadError by viewModel.downloadError.collectAsState()
        
        val snackbarHostState = remember { SnackbarHostState() }
        
        LaunchedEffect(downloadProgress) {
            downloadProgress?.let { progress ->
                if (progress.isComplete) {
                    snackbarHostState.showSnackbar(
                        message = "✓ Saved to vault: ${progress.fileName}",
                        duration = SnackbarDuration.Short
                    )
                    viewModel.clearDownloadProgress()
                }
            }
        }
        
        LaunchedEffect(downloadError) {
            downloadError?.let { error ->
                snackbarHostState.showSnackbar(
                    message = "Download failed: $error",
                    duration = SnackbarDuration.Short
                )
                viewModel.clearDownloadError()
            }
        }
        
        Scaffold(
            containerColor = DarkBackground,
            topBar = {
                Column(
                    modifier = Modifier.background(DarkBackground)
                ) {
                    // Incognito header
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SecureBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = SecureBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Incognito",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                        },
                        actions = {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(VaultGreenSubtle.copy(alpha = 0.3f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = VaultGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Vault",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultGreen
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkBackground
                        )
                    )
                    
                    // URL bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Navigation buttons
                        IconButton(
                            onClick = { webView?.goBack() },
                            enabled = canGoBack
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canGoBack) TextPrimary else TextMuted
                            )
                        }
                        
                        IconButton(
                            onClick = { webView?.goForward() },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) TextPrimary else TextMuted
                            )
                        }
                        
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = TextPrimary
                            )
                        }
                        
                        // URL input
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "Search or enter URL",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        "https://$url"
                                    } else url
                                    webView?.loadUrl(finalUrl)
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                cursorColor = SecureBlue
                            )
                        )
                        
                        IconButton(onClick = {
                            val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                "https://$url"
                            } else url
                            webView?.loadUrl(finalUrl)
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Go",
                                tint = TextPrimary
                            )
                        }
                    }
                    
                    // Loading indicator
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = SecureBlue,
                            trackColor = DarkSurfaceVariant
                        )
                    }
                    
                    // Download progress
                    downloadProgress?.let { progress ->
                        if (!progress.isComplete) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkCard)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = VaultGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Encrypting: ${progress.fileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = VaultGreen
                                )
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        
                        layoutDirection = View.LAYOUT_DIRECTION_LTR
                        
                        clearCache(true)
                        clearHistory()
                        clearFormData()
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            allowFileAccess = false
                            allowContentAccess = false
                            setGeolocationEnabled(false)
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }
                        
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                isLoading = true
                                pageUrl?.let {
                                    currentUrl = it
                                    url = it
                                }
                            }
                            
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }
                            
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            }
                        }
                        
                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                            scope.launch {
                                viewModel.downloadToVault(
                                    url = downloadUrl,
                                    userAgent = userAgent,
                                    contentDisposition = contentDisposition,
                                    mimeType = mimeType
                                )
                            }
                        }
                        
                        loadUrl("https://www.google.com")
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
        
        DisposableEffect(Unit) {
            onDispose {
                webView?.apply {
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
            }
        }
    }
}
