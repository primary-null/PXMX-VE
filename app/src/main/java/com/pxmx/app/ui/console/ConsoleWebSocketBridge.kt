package com.pxmx.app.ui.console

import android.webkit.JavascriptInterface
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

/** Only the transport may create a socket; JavaScript never receives credentials or a native TLS bypass. */
internal class ConsoleWebSocketBridge(
    private val transport: ConsoleTransport,
    private val emit: (id: String, event: String, data: String) -> Unit,
) {
    private val sockets = mutableMapOf<String, WebSocket>()
    private var disposed = false

    @JavascriptInterface
    @Synchronized
    fun connect(id: String, url: String, protocols: String) {
        if (disposed || !id.matches(Regex("[A-Za-z0-9-]{1,80}"))) return
        if (sockets.containsKey(id) || sockets.size >= 8) {
            emit(id, "error", "Console socket limit reached")
            return
        }
        try {
            sockets[id] = transport.openWebSocket(url, protocols, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = active(id, webSocket) {
                    emit(id, "open", response.header("Sec-WebSocket-Protocol").orEmpty())
                }
                override fun onMessage(webSocket: WebSocket, text: String) = active(id, webSocket) {
                    emit(id, "text", text)
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = active(id, webSocket) {
                    emit(id, "binary", bytes.base64())
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = active(id, webSocket) {
                    webSocket.close(code, reason)
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = active(id, webSocket) {
                    sockets.remove(id)
                    emit(id, "close", "$code:$reason")
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = active(id, webSocket) {
                    sockets.remove(id)
                    emit(id, "error", "Console TLS or WebSocket connection failed")
                }
            })
        } catch (_: Exception) {
            emit(id, "error", "Blocked console WebSocket request")
        }
    }

    @JavascriptInterface
    @Synchronized
    fun send(id: String, data: String, binary: Boolean): Boolean {
        val socket = sockets[id] ?: return false
        return if (binary) {
            val bytes = data.decodeBase64() ?: return false
            socket.send(bytes)
        } else socket.send(data)
    }

    @JavascriptInterface
    @Synchronized
    fun close(id: String, code: Int, reason: String) {
        val socket = sockets[id] ?: return
        try {
            socket.close(code, reason)
        } catch (_: IllegalArgumentException) {
            socket.cancel()
        }
    }

    @Synchronized
    fun closeAll() {
        val previous = sockets.values.toList()
        sockets.clear()
        previous.forEach { it.cancel() }
    }

    @Synchronized
    fun dispose() {
        disposed = true
        closeAll()
    }

    @Synchronized
    private fun active(id: String, socket: WebSocket, action: () -> Unit) {
        if (!disposed && sockets[id] === socket) action()
    }
}
