package com.pxmx.app.ui.console

object ConsoleMimeUtils {

    private val JAVASCRIPT_MIMES = setOf(
        "application/javascript",
        "text/javascript",
        "application/x-javascript",
        "text/ecmascript",
        "application/ecmascript",
        "application/x-ecmascript",
    )

    fun isJavaScriptMime(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return false
        return JAVASCRIPT_MIMES.contains(mime.trim().lowercase())
    }

    fun extractCharset(contentTypeHeader: String?): String? {
        if (contentTypeHeader.isNullOrBlank()) return null
        val match = Regex("charset=\\s*[\"']?([^\"';\\s]+)", RegexOption.IGNORE_CASE).find(contentTypeHeader)
        return match?.groupValues?.get(1)?.ifBlank { null }
    }

    /**
     * Resolves and coerces MIME types for intercepted WebView resources.
     * Proxmox noVNC (1.7+) and xterm.js load scripts via ES modules which strictly
     * require a valid JavaScript MIME type per HTML specification. If the server
     * omits Content-Type or sends text/plain or application/octet-stream, this
     * coerces .js / .mjs requests to application/javascript.
     */
    fun coerceMimeType(url: String, contentTypeHeader: String?): String {
        val path = url.substringBefore('?').substringBefore('#')
        val parsedMime = contentTypeHeader?.substringBefore(';')?.trim()?.lowercase()?.ifBlank { null }

        val isJsFile = path.endsWith(".js", ignoreCase = true) || path.endsWith(".mjs", ignoreCase = true)

        if (isJsFile) {
            return "application/javascript"
        }

        if (isJavaScriptMime(parsedMime)) {
            return "application/javascript"
        }

        val isGenericOrMissing = parsedMime == null ||
            parsedMime == "application/octet-stream" ||
            parsedMime == "text/plain"

        if (isGenericOrMissing) {
            when {
                path.endsWith(".css", ignoreCase = true) -> return "text/css"
                path.endsWith(".html", ignoreCase = true) || path.endsWith(".htm", ignoreCase = true) -> return "text/html"
                path.endsWith(".json", ignoreCase = true) -> return "application/json"
                path.endsWith(".svg", ignoreCase = true) -> return "image/svg+xml"
                path.endsWith(".png", ignoreCase = true) -> return "image/png"
                path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> return "image/jpeg"
                path.endsWith(".gif", ignoreCase = true) -> return "image/gif"
                path.endsWith(".ico", ignoreCase = true) -> return "image/x-icon"
                path.endsWith(".wasm", ignoreCase = true) -> return "application/wasm"
                path.endsWith(".woff2", ignoreCase = true) -> return "font/woff2"
                path.endsWith(".woff", ignoreCase = true) -> return "font/woff"
                path.endsWith(".ttf", ignoreCase = true) -> return "font/ttf"
            }
        }

        return parsedMime ?: "application/octet-stream"
    }

    /**
     * Builds response headers for WebResourceResponse, stripping hop-by-hop and length headers
     * that could interfere with WebView stream handling, and ensuring Content-Type matches the
     * coerced MIME type and charset.
     */
    fun buildResponseHeaders(
        rawHeaders: Iterable<Pair<String, String>>,
        mime: String,
        encoding: String?,
    ): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        for ((name, value) in rawHeaders) {
            if (!name.equals("Content-Encoding", ignoreCase = true) &&
                !name.equals("Content-Length", ignoreCase = true) &&
                !name.equals("Transfer-Encoding", ignoreCase = true) &&
                !name.equals("Content-Type", ignoreCase = true)
            ) {
                headers[name] = value
            }
        }
        headers["Content-Type"] = if (encoding != null) "$mime; charset=$encoding" else mime
        return headers
    }

    /**
     * Builds JavaScript to inject into WebView so noVNC / xterm scale properly.
     * When wide is true, terminal uses 100vh; when false (tall portrait), uses 92vh.
     */
    fun buildFitScript(wide: Boolean): String {
        val maxH = if (wide) "100vh" else "92vh"
        return """
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
    }
}
