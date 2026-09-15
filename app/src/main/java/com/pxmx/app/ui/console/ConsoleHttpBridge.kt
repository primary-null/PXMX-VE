package com.pxmx.app.ui.console

import android.webkit.JavascriptInterface
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

internal data class ConsoleHttpResponse(val status: Int, val reason: String, val contentType: String, val body: String)

/** Async, cancellable API2Request transport. No arbitrary headers, redirects, or native fallback. */
internal class ConsoleHttpBridge(
    private val transport: ConsoleTransport,
    private val emit: (id: String, response: ConsoleHttpResponse) -> Unit,
) {
    private val calls = mutableMapOf<String, Call>()
    private var disposed = false

    @JavascriptInterface
    @Synchronized
    fun request(id: String, url: String, method: String, contentType: String, csrf: String, body: String) {
        if (disposed || !id.matches(Regex("[A-Za-z0-9-]{1,80}")) || calls.containsKey(id)) return
        if (calls.size >= 8) {
            emit(id, failure(429, "Console request limit reached"))
            return
        }
        val call = transport.newConsoleApiCall(url, method, contentType, csrf, body)
        if (call == null) {
            emit(id, failure(403, "Blocked console API request"))
            return
        }
        calls[id] = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                complete(id, call, failure(502, "Console TLS or network request failed"))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        if (it.code < 200 || it.code in 300..399) {
                            failure(403, "Blocked console API redirect")
                        } else {
                            ConsoleHttpResponse(it.code, it.message, it.header("Content-Type").orEmpty(), it.body?.string().orEmpty())
                        }
                    }
                } catch (_: IOException) {
                    failure(502, "Console TLS or network request failed")
                }
                complete(id, call, result)
            }
        })
    }

    @JavascriptInterface
    @Synchronized
    fun abort(id: String) {
        calls.remove(id)?.cancel()
    }

    @Synchronized
    fun closeAll() {
        val previous = calls.values.toList()
        calls.clear()
        previous.forEach { it.cancel() }
    }

    @Synchronized
    fun dispose() {
        disposed = true
        closeAll()
    }

    @Synchronized
    private fun complete(id: String, call: Call, response: ConsoleHttpResponse) {
        if (!disposed && calls[id] === call) {
            calls.remove(id)
            emit(id, response)
        }
    }

    private fun failure(status: Int, message: String) = ConsoleHttpResponse(status, message, "text/plain", message)
}
