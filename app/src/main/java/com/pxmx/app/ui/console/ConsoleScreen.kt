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
import com.pxmx.app.data.api.CertUtils
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    val allowedHost = remember(session.cookieHostUrl) {
        val h = Uri.parse(session.cookieHostUrl).host
        if (h.isNullOrBlank() || h.equals("demo", ignoreCase = true)) "demo" else h
    }

    // Fetches the console host's traffic through the app's own TLS stack.
    // The WebView never dials TLS for the console host itself, which avoids
    // the WebView's SSL-proceed path (it breaks ES module script execution,
    // and noVNC 1.7+ ships as a module). The same pin policy as the API layer
    // is enforced inside the trust manager below.
    val fetchClient = remember(session.cookieHostUrl, trustSelfSigned, expectedCertPin) {
        val defaultTm = javax.net.ssl.TrustManagerFactory
            .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as java.security.KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                defaultTm.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (trustSelfSigned) {
                    val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty certificate chain")
                    val pin = expectedCertPin
                    if (pin != null &&
                        CertUtils.normalizeFingerprint(CertUtils.computeSha256Fingerprint(leaf)) !=
                        CertUtils.normalizeFingerprint(pin)
                    ) {
                        throw CertificateException("Certificate changed for host — possible MITM attack!")
                    }
                    // Unpinned: first use of a self-signed host; login already authorized it.
                } else {
                    defaultTm.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTm.acceptedIssuers
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(tm), SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, tm)
            .hostnameVerifier { hostname, _ -> hostname.equals(allowedHost, ignoreCase = true) }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val context = LocalContext.current
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

    // Allocate one WebView instance per ConsoleScreen session to survive recompositions and layout switches.
    val webViewInstance = remember(session) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Console is always HTTPS to the same PVE host; never allow cleartext mix-in.
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            // Let remote desktop scale; pinch still available
            settings.defaultZoom = WebSettings.ZoomDensity.FAR
            @Suppress("DEPRECATION")
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, false)
            cm.setCookie(
                session.cookieHostUrl,
                "PVEAuthCookie=${session.pveAuthCookie}; Path=/; Secure",
            )
            cm.flush()

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                    loading = newProgress < 100
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loading = true
                    errorText = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    view?.let { injectFitScript(it, isWide) }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    errorSsl: SslError?,
                ) {
                    // Guard for any direct load the interceptor did not cover.
                    val sslCert = errorSsl?.certificate
                    val x509Cert = sslCert?.let { CertUtils.getX509Certificate(it) }
                    val presentedFp = x509Cert?.let { CertUtils.computeSha256Fingerprint(it) }

                    if (trustSelfSigned && presentedFp != null && expectedCertPin != null &&
                        CertUtils.normalizeFingerprint(presentedFp) == CertUtils.normalizeFingerprint(expectedCertPin)
                    ) {
                        handler?.proceed()
                    } else {
                        handler?.cancel()
                        loading = false
                        errorText = when {
                            !trustSelfSigned -> "TLS error: untrusted certificate (enable Trust self-signed on login)"
                            expectedCertPin == null -> "TLS error: certificate pin not found for host"
                            presentedFp != null && CertUtils.normalizeFingerprint(presentedFp) != CertUtils.normalizeFingerprint(expectedCertPin) ->
                                "Certificate changed for host — possible MITM attack! (pinned: $expectedCertPin, presented: $presentedFp)"
                            else -> "TLS error: ${errorSsl?.primaryError ?: "Untrusted certificate"}"
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val url = request?.url
                    return url != null && url.scheme != "data" && url.host != allowedHost && allowedHost != "demo"
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val reqUrl = request?.url ?: return null
                    val reqStr = reqUrl.toString()
                    if (reqUrl.scheme != "https" || !reqStr.startsWith(session.cookieHostUrl)) {
                        return null
                    }
                    if (request.method != "GET" && request.method != "HEAD") {
                        return null
                    }
                    return try {
                        val rb = Request.Builder().url(reqStr)
                        request.requestHeaders.forEach { (k, v) ->
                            if (!k.equals("Cookie", ignoreCase = true)) rb.addHeader(k, v)
                        }
                        rb.addHeader("Cookie", "PVEAuthCookie=${session.pveAuthCookie}")
                        fetchClient.newCall(rb.build()).execute().use { resp ->
                            val rawBody = resp.body?.bytes() ?: byteArrayOf()
                            val rawContentType = resp.header("Content-Type")
                            val mime = ConsoleMimeUtils.coerceMimeType(reqStr, rawContentType)
                            val encoding = ConsoleMimeUtils.extractCharset(rawContentType)
                            val headers = ConsoleMimeUtils.buildResponseHeaders(
                                resp.headers.map { it.first to it.second },
                                mime,
                                encoding,
                            )
                            WebResourceResponse(
                                mime,
                                encoding,
                                resp.code,
                                resp.message.ifBlank { "OK" },
                                headers,
                                ByteArrayInputStream(rawBody),
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
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
            } else {
                loadUrl(session.pageUrl)
            }
        }
    }

    DisposableEffect(session) {
        onDispose {
            runCatching {
                val cm = CookieManager.getInstance()
                cm.setCookie(session.cookieHostUrl, "PVEAuthCookie=; Max-Age=0; Path=/")
                cm.flush()
            }
            webViewInstance.stopLoading()
            webViewInstance.destroy()
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
                                    label = "Reload",
                                    icon = Icons.Default.Refresh,
                                    onClick = { webViewInstance.reload() },
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
                            IconButton(onClick = {
                                webViewInstance.reload()
                            }) {
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

