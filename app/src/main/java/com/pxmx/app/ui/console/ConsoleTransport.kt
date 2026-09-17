package com.pxmx.app.ui.console

import com.pxmx.app.data.model.GuestType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal data class ConsoleResource(
    val mime: String,
    val encoding: String?,
    val status: Int,
    val reason: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

/** Never returns null: that would ask WebView to bypass the app's TLS policy. */
internal class ConsoleTransport(
    origin: String,
    private val cookie: String,
    client: OkHttpClient,
    private val socketScript: String = "",
    private val node: String = "",
    private val guestType: GuestType? = null,
    private val vmid: Long = 0,
) {
    private val endpoint = origin.toHttpUrl()
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    fun allows(url: String): Boolean {
        val target = url.toHttpUrlOrNull() ?: return false
        return target.scheme == "https" && target.host == endpoint.host && target.port == endpoint.port &&
            target.username.isEmpty() && target.password.isEmpty()
    }

    fun openWebSocket(url: String, protocols: String, listener: okhttp3.WebSocketListener): okhttp3.WebSocket {
        require(url.startsWith("wss://") && allows("https://" + url.removePrefix("wss://")))
        val requestedProtocols = protocols.split(',').filter { it.isNotEmpty() }
        require(requestedProtocols.all { it.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) })
        require(requestedProtocols.distinct().size == requestedProtocols.size)
        val request = Request.Builder().url(url)
            .header("Cookie", "PVEAuthCookie=$cookie")
            .header("Origin", endpoint.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/"))
            .apply { if (requestedProtocols.isNotEmpty()) header("Sec-WebSocket-Protocol", requestedProtocols.joinToString(", ")) }
            .build()
        return client.newWebSocket(request, listener)
    }

    // XHR's body cannot cross shouldInterceptRequest. The explicit API bridge
    // supplies it, while fetch below continues to deny native POST interception.
    // Share the TLS policy, but never retry/replay a console mutation or redirect.
    private val apiClient = this.client.newBuilder().retryOnConnectionFailure(false)
        .callTimeout(60, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            // OkHttp retries 503 when Retry-After is 0; that would replay a console mutation.
            chain.proceed(chain.request()).newBuilder().removeHeader("Retry-After").build()
        }
        .build()

    fun newConsoleApiCall(url: String, method: String, contentType: String, csrf: String, body: String): Call? {
        if (!allows(url) || guestType == null || node.isEmpty()) return null
        if ((guestType == GuestType.QEMU || guestType == GuestType.LXC) && vmid <= 0) return null
        val target = url.toHttpUrl()
        if (target.fragment != null || body.toByteArray(Charsets.UTF_8).size > 65536) return null
        val path = target.encodedPath
        val nodePrefix = "/api2/json/nodes/${Regex.escape(node)}"
        val guestPrefix = when (guestType) {
            GuestType.QEMU, GuestType.LXC -> "$nodePrefix/${guestType.path}/$vmid"
            GuestType.NODE -> nodePrefix
        }
        val request = Request.Builder().url(target)
            .header("Cookie", "PVEAuthCookie=$cookie")
            .header("Cache-Control", "no-cache")
        try {
            when (method) {
                "POST" -> {
                    val allowed = when (guestType) {
                        GuestType.NODE -> Regex("$nodePrefix/(?:vncproxy|termproxy|vncshell)")
                        GuestType.QEMU -> Regex("$guestPrefix/(?:vncproxy|termproxy)|$guestPrefix/status/(?:start|shutdown|stop|reset|suspend|resume)")
                        GuestType.LXC -> Regex("$guestPrefix/(?:vncproxy|termproxy)|$guestPrefix/status/(?:start|shutdown|stop)")
                    }
                    if (target.query != null || !allowed.matches(path)) return null
                    if (!contentType.equals("application/x-www-form-urlencoded", true) || csrf.isBlank()) return null
                    request.header("CSRFPreventionToken", csrf)
                        .post(body.toByteArray(Charsets.UTF_8).toRequestBody(contentType.toMediaType()))
                }
                "GET" -> {
                    val guestRead = when (guestType) {
                        GuestType.QEMU, GuestType.LXC -> Regex("$guestPrefix/(?:status/current|config)")
                        GuestType.NODE -> null
                    }
                    if (body.isNotEmpty() || !(path == "/api2/json/cluster/resources" || guestRead?.matches(path) == true)) return null
                    request.get()
                }
                else -> return null
            }
            return apiClient.newCall(request.build())
        } catch (_: IllegalArgumentException) {
            return null
        }
    }

    fun fetch(url: String, method: String, requestHeaders: Map<String, String>): ConsoleResource {
        if (!allows(url) || (method != "GET" && method != "HEAD")) return blocked()
        return try {
            var target = url.toHttpUrl()
            repeat(6) {
                val rb = Request.Builder().url(target)
                requestHeaders.forEach { (k, v) ->
                    if (!k.equals("Cookie", ignoreCase = true)) rb.addHeader(k, v)
                }
                rb.addHeader("Cookie", "PVEAuthCookie=$cookie")
                client.newCall(rb.build()).execute().use { resp ->
                    if (resp.isRedirect) {
                        val next = resp.header("Location")?.let { target.resolve(it) }
                        if (next == null || !allows(next.toString())) return blocked()
                        target = next
                    } else {
                        // WebResourceResponse cannot represent redirects or informational statuses.
                        if (resp.code < 200 || resp.code in 300..399) return blocked()
                        val rawBody = resp.body?.bytes() ?: byteArrayOf()
                        val contentType = resp.header("Content-Type")
                        val mime = ConsoleMimeUtils.coerceMimeType(target.toString(), contentType)
                        val sourceEncoding = ConsoleMimeUtils.extractCharset(contentType)
                        val isHtml = mime == "text/html"
                        val encoding = if (isHtml) "UTF-8" else sourceEncoding
                        val body = if (isHtml) {
                            val html = rawBody.toString(java.nio.charset.Charset.forName(sourceEncoding ?: "UTF-8"))
                            val doctype = Regex("(?is)^(?:\\uFEFF)?\\s*<!doctype[^>]*>").find(html)
                            val position = doctype?.range?.last?.plus(1) ?: 0
                            (html.take(position) + "<script>" + socketScript + "</script>" + html.drop(position)).toByteArray(Charsets.UTF_8)
                        } else rawBody
                        val headers = ConsoleMimeUtils.buildResponseHeaders(resp.headers.map { it.first to it.second }, mime, encoding)
                            .filterKeys { !it.equals("Set-Cookie", true) && !it.equals("Cache-Control", true) && !it.equals("Content-Security-Policy", true) }
                            .toMutableMap()
                        headers["Cache-Control"] = "no-store"
                        if (isHtml) {
                            // Workers and frames have separate network stacks and must not recover a native WebSocket.
                            headers["Content-Security-Policy"] = "connect-src 'self'; frame-src 'none'; worker-src 'none'; object-src 'none'; form-action 'none'; base-uri 'self'"
                        }
                        return ConsoleResource(mime, encoding, resp.code, resp.message.ifBlank { "OK" }, headers, body)
                    }
                }
            }
            blocked()
        } catch (_: Exception) {
            ConsoleResource("text/plain", "UTF-8", 502, "Console transport failed",
                mapOf("Cache-Control" to "no-store"),
                "Console TLS or network request failed. Check the certificate pin and sign in again.".toByteArray())
        }
    }

    private fun blocked() = ConsoleResource("text/plain", "UTF-8", 403, "Blocked console request",
        mapOf("Cache-Control" to "no-store"), "Blocked console request".toByteArray())
}
