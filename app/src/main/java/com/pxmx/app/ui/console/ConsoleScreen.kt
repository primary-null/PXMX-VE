package com.pxmx.app.ui.console

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.pxmx.app.data.model.ConsoleSession
import com.pxmx.app.ui.adaptive.isWideViewport
import com.pxmx.app.ui.components.TechActionPlate
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechStatusPlate
import com.pxmx.app.ui.components.techTopAppBarColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pxmx.app.ui.util.findActivity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pxmx.app.data.model.GuestType
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Proxmox noVNC / xterm.js console with mobile fit:
 * - portrait: scale to width, keep usable
 * - landscape: expand to fill screen (optional chrome hide)
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConsoleScreen(
    session: ConsoleSession,
    trustSelfSigned: Boolean,
    expectedCertPin: String? = null,
    onBack: () -> Unit,
    isTabletop: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val isWide = isWideViewport(configuration.screenWidthDp, configuration.screenHeightDp)
    var immersive by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val allowedHost = remember(session.cookieHostUrl) {
        val h = Uri.parse(session.cookieHostUrl).host
        if (h.isNullOrBlank() || h.equals("demo", ignoreCase = true)) "demo" else h
    }

    val context = LocalContext.current
    // Resources and WebSockets share the app's TLS client. WebView's native
    // network stack cannot enforce a pin on a platform-trusted replacement.
    val fetchClient = remember(session.cookieHostUrl, trustSelfSigned, expectedCertPin) {
        createConsoleClient(allowedHost, trustSelfSigned, expectedCertPin)
    }
    val transport = remember(session, fetchClient) {
        val socketScript = context.assets.open("console-websocket.js").bufferedReader().use { it.readText() }
        ConsoleTransport(
            session.cookieHostUrl,
            session.pveAuthCookie,
            fetchClient,
            socketScript,
            session.node,
            session.guestType,
            session.vmid,
            session.apiToken.ifBlank { null },
        )
    }
    val bridgeHolder = remember(transport) { arrayOfNulls<ConsoleWebSocketBridge>(1) }
    val httpBridgeHolder = remember(transport) { arrayOfNulls<ConsoleHttpBridge>(1) }

    val view = LocalView.current
    var userOrientation by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(userOrientation) {
        runCatching {
            val activity = context.findActivity() ?: view.context.findActivity()
            activity?.requestedOrientation = userOrientation ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    DisposableEffect(Unit) {
        runCatching {
            val activity = context.findActivity() ?: view.context.findActivity()
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            runCatching {
                val activity = context.findActivity() ?: view.context.findActivity()
                val window = activity?.window
                if (window != null) {
                    WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
                }
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Default landscape to immersive for more console pixels
    // Back handling: exit immersive mode first if active, then step back to caller screen
    BackHandler(enabled = immersive) {
        immersive = false
    }
    BackHandler(enabled = !immersive) {
        onBack()
    }

    val isDisposed = remember(session, transport) { booleanArrayOf(false) }
    val acceptLastStill = remember(session, transport) { booleanArrayOf(false) }

    // Allocate one WebView instance per ConsoleScreen session to survive recompositions and layout switches.
    val webViewInstance = remember(session, transport) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            // Intercepted resources still work; everything else (including native
            // WebSockets, native POSTs and worker traffic) fails closed.
            settings.blockNetworkLoads = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Console is always HTTPS to the same PVE host; never allow cleartext mix-in.
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            // Let remote desktop scale; pinch still available
            @Suppress("DEPRECATION")
            settings.defaultZoom = WebSettings.ZoomDensity.FAR
            @Suppress("DEPRECATION")
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

            // Do not give WebView an authentication cookie. Clear older console
            // cookies; only ConsoleTransport attaches the current ticket.
            val cm = CookieManager.getInstance()
            cm.setAcceptThirdPartyCookies(this, false)
            cm.setCookie(session.cookieHostUrl, "PVEAuthCookie=; Max-Age=0; Path=/; Secure")
            cm.flush()
            val bridge = ConsoleWebSocketBridge(transport) { id, event, data ->
                post {
                    if (!isDisposed[0]) {
                        if (event == "error") {
                            loading = false
                            errorText = "Console TLS or WebSocket connection failed. Check the certificate pin and sign in again."
                        }
                        val args = listOf(id, event, data).joinToString(",", transform = ConsoleMimeUtils::escapeJsString)
                        evaluateJavascript("window.__pxmxSocketEvent && window.__pxmxSocketEvent($args)", null)
                    }
                }
            }
            bridgeHolder[0] = bridge
            addJavascriptInterface(bridge, "PXMXConsoleSocket")
            val httpBridge = ConsoleHttpBridge(transport) { id, response ->
                post {
                    if (!isDisposed[0]) {
                        val args = listOf(response.reason, response.contentType, response.body)
                            .joinToString(",", transform = ConsoleMimeUtils::escapeJsString)
                        val requestId = ConsoleMimeUtils.escapeJsString(id)
                        evaluateJavascript("window.__pxmxHttpEvent && window.__pxmxHttpEvent($requestId,${response.status},$args)", null)
                    }
                }
            }
            httpBridgeHolder[0] = httpBridge
            addJavascriptInterface(httpBridge, "PXMXConsoleHttp")

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                    loading = newProgress < 100
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    bridge.closeAll()
                    httpBridge.closeAll()
                    loading = true
                    errorText = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    view?.let { injectFitScript(it, isWide) }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    errorSsl: SslError?,
                ) {
                    handler?.cancel()
                    loading = false
                    errorText = "Blocked native console TLS load"
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = request?.url?.toString()?.let { !transport.allows(it) } ?: true

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val resource = transport.fetch(request?.url?.toString().orEmpty(), request?.method.orEmpty(), request?.requestHeaders.orEmpty())
                    if (resource.status >= 400 && request?.isForMainFrame == true) {
                        val bodyText = String(resource.body, Charsets.UTF_8).trim()
                        val err = if (bodyText.isNotBlank()) bodyText else resource.reason
                        view?.post {
                            if (!isDisposed[0]) {
                                loading = false
                                errorText = err
                            }
                        }
                    }
                    return WebResourceResponse(resource.mime, resource.encoding, resource.status,
                        resource.reason, resource.headers, ByteArrayInputStream(resource.body))
                }
            }

            if (session.pageUrl.startsWith("data:")) {
                val html = try {
                    java.net.URLDecoder.decode(
                        session.pageUrl.removePrefix("data:text/html;charset=utf-8,").removePrefix("data:text/html,"),
                        "UTF-8",
                    )
                } catch (_: Exception) {
                    session.pageUrl
                }
                loadDataWithBaseURL("https://demo:8006", html, "text/html", "UTF-8", null)
            }
        }
    }

    val loadJobHolder = remember { arrayOfNulls<Job>(1) }
    val loadConsoleDocument: () -> Unit = {
        if (session.pageUrl.startsWith("data:")) {
            val html = try {
                java.net.URLDecoder.decode(
                    session.pageUrl.removePrefix("data:text/html;charset=utf-8,").removePrefix("data:text/html,"),
                    "UTF-8",
                )
            } catch (_: Exception) {
                session.pageUrl
            }
            webViewInstance.loadDataWithBaseURL("https://demo:8006", html, "text/html", "UTF-8", null)
        } else {
            loading = true
            errorText = null
            loadJobHolder[0]?.cancel()
            loadJobHolder[0] = scope.launch(Dispatchers.IO) {
                try {
                    val resource = transport.fetch(session.pageUrl, "GET", emptyMap())
                    ensureActive()
                    if (resource.status in 200..299 && resource.mime == "text/html") {
                        val html = resource.body.toString(Charsets.UTF_8)
                        webViewInstance.post {
                            if (!isDisposed[0]) {
                                webViewInstance.loadDataWithBaseURL(session.pageUrl, html, "text/html", "UTF-8", null)
                            }
                        }
                    } else {
                        val bodyText = String(resource.body, Charsets.UTF_8).trim()
                        val err = if (resource.status >= 400 && bodyText.isNotBlank()) {
                            bodyText
                        } else {
                            resource.reason
                        }
                        webViewInstance.post {
                            if (!isDisposed[0]) {
                                loading = false
                                errorText = err
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    webViewInstance.post {
                        if (!isDisposed[0]) {
                            loading = false
                            errorText = "Console transport failed"
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(session, transport) {
        if (!session.pageUrl.startsWith("data:")) {
            loadConsoleDocument()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as? com.pxmx.app.ProxmoxApp
    val stillStore = app?.consoleStillStore
    val sessionStore = app?.sessionStore

    val profileId = remember(session.cookieHostUrl) {
        if (session.cookieHostUrl.contains("demo", ignoreCase = true) ||
            sessionStore?.session?.value?.config?.host.equals("demo", ignoreCase = true)
        ) {
            "demo"
        } else {
            sessionStore?.snapshot()?.profileId
                ?: sessionStore?.lastProfileId()
                ?: "session"
        }
    }

    val captureStill: () -> Unit = {
        if (session.guestType == GuestType.QEMU && !isDisposed[0]) {
            try {
                webViewInstance.evaluateJavascript(ConsoleMimeUtils.buildStillCaptureScript()) { result ->
                    if ((!isDisposed[0] || acceptLastStill[0]) && !result.isNullOrBlank() && result != "null") {
                        val bytes = ConsoleMimeUtils.parseDataUrlJpeg(result)
                        if (bytes != null && bytes.isNotEmpty()) {
                            val still = com.pxmx.app.data.console.ConsoleStill(
                                jpegBytes = bytes,
                                timestampEpochMs = System.currentTimeMillis(),
                            )
                            stillStore?.putStill(profileId, session.guestType, session.vmid, still)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Ignore JS / WebView errors
            }
        }
    }

    DisposableEffect(lifecycleOwner, session) {
        if (session.guestType != GuestType.QEMU) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    captureStill()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                acceptLastStill[0] = true
                captureStill()
            }
        }
    }

    LaunchedEffect(session) {
        if (session.guestType == GuestType.QEMU) {
            while (isActive) {
                delay(2500L)
                captureStill()
            }
        }
    }

    DisposableEffect(webViewInstance) {
        onDispose {
            acceptLastStill[0] = true
            captureStill()
            isDisposed[0] = true
            loadJobHolder[0]?.cancel()
            runCatching {
                val cm = CookieManager.getInstance()
                cm.setCookie(session.cookieHostUrl, "PVEAuthCookie=; Max-Age=0; Path=/")
                cm.flush()
            }
            bridgeHolder[0]?.dispose()
            httpBridgeHolder[0]?.dispose()
            webViewInstance.removeJavascriptInterface("PXMXConsoleSocket")
            webViewInstance.removeJavascriptInterface("PXMXConsoleHttp")
            teardownConsoleClientAsync(fetchClient, scope)
            webViewInstance.stopLoading()
            webViewInstance.destroy()
        }
    }

    val onPaste: () -> Unit = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!clipText.isNullOrEmpty()) {
            webViewInstance.evaluateJavascript(ConsoleMimeUtils.buildPasteScript(clipText), null)
        }
    }

    // Re-apply fit when rotating or sizing
    LaunchedEffect(isWide, immersive) {
        injectFitScript(webViewInstance, isWide)
    }

    @Composable
    fun ConsoleWebViewBox(modifier: Modifier = Modifier) {
        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { _ ->
                    (webViewInstance.parent as? ViewGroup)?.removeView(webViewInstance)
                    webViewInstance
                },
                update = { view ->
                    injectFitScript(view, isWide)
                },
                onRelease = { _ -> },
            )

            if (loading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
            val err = errorText
            if (err != null) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            } else if (loading && progress < 0.05f) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (isTabletop) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
        ) {
            if (immersive) {
                // Full column when immersive: collapse the lower dock
                Box(modifier = Modifier.fillMaxSize()) {
                    ConsoleWebViewBox(modifier = Modifier.fillMaxSize())

                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        IconButton(onClick = {
                            userOrientation = if (isWide) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onPaste) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { immersive = false }) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Show controls",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            } else {
                // Upper region: terminal / guest display above the fold
                ConsoleWebViewBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                // Lower region: top-bar actions below the fold
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(TechColors.Hull)
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "CONSOLE · ${session.name.uppercase()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TechStatusPlate(status = if (loading) "CONNECTING" else "ONLINE")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    append(session.guestType.label)
                                    append(" ")
                                    append(session.vmid)
                                    append(" · ")
                                    append(session.node)
                                    append(if (isWide) " · wide" else " · portrait")
                                    append(" · tabletop")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        TechDeck(
                            modifier = Modifier.fillMaxWidth(),
                            showAccentBar = true,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TechActionPlate(
                                    label = "Back",
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    onClick = onBack,
                                )
                                TechActionPlate(
                                    label = "Rotate",
                                    icon = Icons.Default.ScreenRotation,
                                    onClick = {
                                        userOrientation = if (isWide) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    },
                                )
                                TechActionPlate(
                                    label = if (immersive) "Window" else "Full",
                                    icon = Icons.Default.Fullscreen,
                                    onClick = { immersive = !immersive },
                                    emphasized = immersive,
                                )
                                TechActionPlate(
                                    label = "Paste",
                                    icon = Icons.Default.ContentPaste,
                                    onClick = onPaste,
                                )
                                TechActionPlate(
                                    label = "Reload",
                                    icon = Icons.Default.Refresh,
                                    onClick = loadConsoleDocument,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                if (!immersive) {
                    TopAppBar(
                        colors = techTopAppBarColors(),
                        title = {
                            Column {
                                Text("Console · ${session.name}")
                                Text(
                                    buildString {
                                        append(session.guestType.label)
                                        append(" ")
                                        append(session.vmid)
                                        append(" · ")
                                        append(session.node)
                                        append(if (isWide) " · wide" else " · portrait")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                userOrientation = if (isWide) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }) {
                                Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate Screen")
                            }
                            IconButton(onClick = { immersive = !immersive }) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen")
                            }
                            IconButton(onClick = onPaste) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                            IconButton(onClick = loadConsoleDocument) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload")
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (immersive) Modifier else Modifier.padding(padding)),
            ) {
                ConsoleWebViewBox(modifier = Modifier.fillMaxSize())

                if (immersive) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        IconButton(onClick = {
                            userOrientation = if (isWide) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onPaste) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { immersive = false }) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Show toolbar",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inject CSS/JS so noVNC / xterm scale into the phone viewport.
 * Wide: fill available area. Tall: fit width.
 */
private fun injectFitScript(webView: WebView, wide: Boolean) {
    webView.evaluateJavascript(ConsoleMimeUtils.buildFitScript(wide), null)
}

