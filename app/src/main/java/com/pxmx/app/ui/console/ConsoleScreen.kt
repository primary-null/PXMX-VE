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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.pxmx.app.data.api.CertUtils
import com.pxmx.app.data.model.ConsoleSession
import com.pxmx.app.ui.util.findActivity

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
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var immersive by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val allowedHost = remember(session.cookieHostUrl) {
        val h = Uri.parse(session.cookieHostUrl).host
        if (h.isNullOrBlank() || h.equals("demo", ignoreCase = true)) "demo" else h
    }

    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        runCatching {
            val activity = context.findActivity() ?: view.context.findActivity()
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                val cm = CookieManager.getInstance()
                cm.setCookie(session.cookieHostUrl, "PVEAuthCookie=; Max-Age=0; Path=/")
                cm.flush()
            }
            webView?.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                removeAllViews()
                // Don't destroy on config change if activity keeps instance —
                // still destroy when leaving console route.
                destroy()
            }
        }
    }

    // Re-apply fit when rotating
    LaunchedEffect(landscape, immersive, webView) {
        webView?.let { injectFitScript(it, landscape) }
    }

    Scaffold(
        topBar = {
            if (!immersive) {
                TopAppBar(
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
                                    append(if (landscape) " · landscape" else " · portrait")
                                },
                                style = MaterialTheme.typography.bodySmall,
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
                        IconButton(onClick = { immersive = !immersive }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen")
                        }
                        IconButton(onClick = {
                            webView?.reload()
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
            if (immersive) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    IconButton(onClick = { immersive = false }) {
                        Icon(
                            Icons.Default.ScreenRotation,
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

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
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
                                view?.let { injectFitScript(it, landscape) }
                            }

                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                errorSsl: SslError?,
                            ) {
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
                                if (url != null && url.scheme != "data" && url.host != allowedHost && allowedHost != "demo") {
                                    return true // Block
                                }
                                return false
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
                        webView = this
                    }
                },
                update = { view ->
                    // Orientation change — re-fit without full reload
                    injectFitScript(view, landscape)
                },
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
}

/**
 * Inject CSS/JS so noVNC / xterm scale into the phone viewport.
 * Portrait: fit width. Landscape: fill available area.
 */
private fun injectFitScript(webView: WebView, landscape: Boolean) {
    val maxH = if (landscape) "100vh" else "92vh"
    val js = """
        (function() {
          try {
            var cssId = 'pve-mobile-fit';
            var old = document.getElementById(cssId);
            if (old) old.remove();
            var style = document.createElement('style');
            style.id = cssId;
            style.textContent = `
              html, body {
                margin: 0 !important;
                padding: 0 !important;
                width: 100% !important;
                height: 100% !important;
                overflow: hidden !important;
                background: #000 !important;
              }
              /* Hide bulky PVE chrome when possible */
              .pve-console-controls, #pve-console-toolbar { max-height: 36px !important; }
              #noVNC_container {
                width: 100vw !important;
                height: 100% !important;
              }
              #noVNC_screen, #noVNC_canvas_area {
                width: 100% !important;
                height: 100% !important;
                max-width: 100vw !important;
                max-height: 100vh !important;
              }
              #terminal-container, .xterm, .xterm-viewport, .xterm-screen {
                width: 100% !important;
                height: ${maxH} !important;
                max-width: 100vw !important;
              }
            `;
            document.head.appendChild(style);

            // Hide noVNC fullscreen button (WebView doesn't support it + we have our own)
            try {
              var fs = document.getElementById('noVNC_fullscreen_button');
              if (fs) fs.style.display = 'none';
            } catch(e) {}

            // Shim Fullscreen API to prevent errors
            if (!Element.prototype.requestFullscreen) {
              Element.prototype.requestFullscreen = function() { return Promise.resolve(); };
            }
            if (!document.exitFullscreen) {
              document.exitFullscreen = function() { return Promise.resolve(); };
            }

            // Engage noVNC native scaling if present
            var tries = 0;
            var iv = setInterval(function() {
              tries++;
              try {
                if (window.UI && UI.rfb) {
                  UI.setSetting('resize', 'scale');
                  UI.applyResizeMode();
                  window.dispatchEvent(new Event('resize'));
                }
              } catch(e) {}
              if (tries > 40) {
                clearInterval(iv);
              }
            }, 500);
            setTimeout(function() { clearInterval(iv); }, 20000);

            window.addEventListener('resize', function() {
              try {
                if (window.UI && UI.rfb) {
                  UI.applyResizeMode();
                }
              } catch(e) {}
            });

            function fallbackScale() {
              if (window.UI && UI.rfb) return;
              var canvas = document.querySelector('canvas');
              if (!canvas) return;
              var vw = window.innerWidth || document.documentElement.clientWidth;
              var vh = window.innerHeight || document.documentElement.clientHeight;
              var cw = canvas.width || canvas.clientWidth || 1;
              var ch = canvas.height || canvas.clientHeight || 1;
              var scale = Math.min(vw / cw, vh / ch);
              if (!isFinite(scale) || scale <= 0) scale = 1;
              // Portrait: prefer fit-width if height allows slight letterbox
              if (vw < vh) {
                scale = Math.min(vw / cw, (vh * 0.92) / ch);
              }
              canvas.style.transformOrigin = 'top left';
              canvas.style.transform = 'scale(' + scale + ')';
              if (canvas.parentElement) {
                canvas.parentElement.style.width = (cw * scale) + 'px';
                canvas.parentElement.style.height = (ch * scale) + 'px';
                canvas.parentElement.style.overflow = 'hidden';
                canvas.parentElement.style.margin = '0 auto';
              }
            }
          } catch (e) {}
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}
