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
                // destroy() lives in AndroidView.onRelease so it runs after
                // the view is detached from the hierarchy.
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
                                // WebResourceRequest does not expose request bodies, so POSTs
                                // cannot be proxied; let the WebView send them natively (the
                                // SSL-proceed path handles the self-signed handshake).
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
                                        val contentType = resp.header("Content-Type")
                                        val mime = contentType?.substringBefore(';') ?: "application/octet-stream"
                                        val encoding = contentType?.substringAfter("charset=", "")?.ifBlank { null }
                                        val isMainHtml = request.isForMainFrame &&
                                            mime.equals("text/html", ignoreCase = true)

                                        // The console page boots noVNC through an ES module import
                                        // whose scripts did not execute reliably through the
                                        // self-signed TLS path. Rewrite the served HTML with a
                                        // classic bootstrap: strip the page's module boot (so it
                                        // is the single start path) and start the bundle manually.
                                        val body = if (isMainHtml) {
                                            var html = String(rawBody, Charsets.UTF_8)
                                            // Remove the page's own ES module boot; our classic
                                            // bootstrap below is the single start path.
                                            html = Regex("<script[^>]*type\\s*=\\s*[\"']?module[\"']?[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE).replace(html, "")
                                            val ver = Regex("error-handler\\.js\\?ver=([^\"']+)")
                                                .find(html)?.groupValues?.get(1) ?: "1.7.0-2"
                                            val boot = """
                                                <script>
                                                (function(){
                                                  if (window.__pxmxBoot) return; window.__pxmxBoot = true;
                                                  function boot(){
                                                    fetch('/novnc/app.js?ver=${ver}')
                                                      .then(function(r){return r.text()})
                                                      .then(function(t){
                                                        var classic = t.replace(/export\s*\{[^}]*\};?/g, '');
                                                        classic = classic.replace(/var ui_default = UI;/, 'var ui_default = UI; window.__UI = UI;');
                                                        var s = document.createElement('script');
                                                        s.textContent = classic;
                                                        document.head.appendChild(s);
                                                        var tries = 0;
                                                        var iv = setInterval(function(){
                                                          tries++;
                                                          if (window.__UI) {
                                                            clearInterval(iv);
                                                            try {
                                                              // The noVNC fork mints fresh console tickets
                                                              // itself via POST (sent natively, since
                                                              // intercepted POSTs cannot carry a body);
                                                              // its reconnect path relies on that.
                                                              window.__UI.start({settings:{defaults:{},mandatory:{}}});
                                                            } catch(e) {}
                                                          } else if (tries > 100) { clearInterval(iv); }
                                                        }, 100);
                                                      });
                                                  }
                                                  if (document.readyState === 'loading') {
                                                    document.addEventListener('DOMContentLoaded', boot);
                                                  } else { boot(); }
                                                })();
                                                </script>
                                            """.trimIndent()
                                            val injected = if (html.contains("</body>")) {
                                                html.replace("</body>", "$boot</body>")
                                            } else {
                                                html + boot
                                            }
                                            injected.toByteArray(Charsets.UTF_8)
                                        } else {
                                            rawBody
                                        }
                                        val headers = mutableMapOf<String, String>()
                                        resp.headers.forEach { pair ->
                                            val name = pair.first
                                            if (!name.equals("Content-Encoding", ignoreCase = true) &&
                                                !name.equals("Content-Length", ignoreCase = true) &&
                                                !name.equals("Transfer-Encoding", ignoreCase = true)
                                            ) {
                                                headers[name] = pair.second
                                            }
                                        }
                                        WebResourceResponse(
                                            mime,
                                            encoding,
                                            resp.code,
                                            resp.message,
                                            headers,
                                            ByteArrayInputStream(body),
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
                        webView = this
                    }
                },
                update = { view ->
                    // Orientation change — re-fit without full reload
                    injectFitScript(view, landscape)
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
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
