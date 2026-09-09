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
}
