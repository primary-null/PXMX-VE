package com.pxmx.app.ui.console

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Wiring guards complement the real TLS/socket tests; these are not Android instrumentation. */
class ConsoleWebViewBoundaryTest {
    @Test
    fun `WebView cannot fall back to native network or SSL proceed`() {
        val file = listOf(
            File("src/main/java/com/pxmx/app/ui/console/ConsoleScreen.kt"),
            File("app/src/main/java/com/pxmx/app/ui/console/ConsoleScreen.kt"),
        ).first { it.exists() }
        val source = file.readText()

        val forbiddenRegex = Regex("""\b(loadUrl|postUrl|reload)\s*\(""")
        val consoleDir = listOf(
            File("src/main/java/com/pxmx/app/ui/console"),
            File("app/src/main/java/com/pxmx/app/ui/console"),
        ).first { it.exists() && it.isDirectory }
        val ktFiles = consoleDir.listFiles { f -> f.extension == "kt" }?.toList().orEmpty()
        assertTrue("Must find console source files", ktFiles.isNotEmpty())
        for (ktFile in ktFiles) {
            val match = forbiddenRegex.find(ktFile.readText())
            assertNull("Forbidden call found in ${ktFile.name}: ${match?.value}", match)
        }

        assertTrue("WebSocket and other non-intercepted native loads must be disabled", source.contains("settings.blockNetworkLoads = true"))
        assertFalse("Live sessions must not use native loadUrl", source.contains("loadUrl(session.pageUrl)"))
        assertFalse("Reload action must not call webViewInstance.reload()", source.contains("webViewInstance.reload()"))
        assertFalse("ConsoleScreen must not call reload() on WebView", source.contains(".reload()"))
        assertFalse("Never approve native TLS outside the pinned client", source.contains("handler?.proceed()"))
        assertFalse("Never call handler.proceed()", source.contains("handler.proceed()"))
        assertFalse("Keep authentication cookies out of the native network stack", source.contains("PVEAuthCookie=${'$'}{session.pveAuthCookie}"))
        assertTrue("Document loading must use loadDataWithBaseURL", source.contains("loadDataWithBaseURL"))
        assertTrue("Live HTML base URL must be session.pageUrl so PVE query vars survive", source.contains("loadDataWithBaseURL(session.pageUrl"))
        assertTrue("Live page must be fetched through ConsoleTransport", source.contains("transport.fetch(session.pageUrl"))
        assertTrue("Document fetch and reload must run off the main thread", source.contains("Dispatchers.IO"))
        assertTrue(source.contains("addJavascriptInterface"))
        assertTrue(source.contains("ConsoleWebSocketBridge"))
        assertTrue("Form POSTs require a pinned body bridge", source.contains("addJavascriptInterface(httpBridge, \"PXMXConsoleHttp\")"))
        assertTrue(source.contains("httpBridge.closeAll()"))
        assertTrue(source.contains("httpBridgeHolder[0]?.dispose()"))
        assertTrue(source.contains("removeJavascriptInterface(\"PXMXConsoleHttp\")"))
        assertTrue(source.contains("WebSettings.LOAD_NO_CACHE"))
        assertFalse("OkHttp connectionPool.evictAll must not be called directly on the main thread in onDispose", source.contains("fetchClient.connectionPool.evictAll()"))
        assertTrue("OkHttp teardown must use teardownConsoleClientAsync with owned scope off the main thread", source.contains("teardownConsoleClientAsync(fetchClient, scope)"))
        assertTrue("ConsoleScreen must remember an owned CoroutineScope for teardown", source.contains("val scope = rememberCoroutineScope()"))
    }

    @Test
    fun `live HTML from ConsoleTransport fetch is injected with socket bootstrap before server scripts and replaces WebSocket and XMLHttpRequest`() {
        val assetFile = listOf(
            File("src/main/assets/console-websocket.js"),
            File("app/src/main/assets/console-websocket.js"),
        ).first { it.exists() }
        val socketScript = assetFile.readText()

        assertTrue(
            "console-websocket.js must replace window.WebSocket",
            socketScript.contains("Object.defineProperty(window, 'WebSocket', {value: ConsoleWebSocket")
        )
        assertTrue(
            "console-websocket.js must replace window.XMLHttpRequest",
            socketScript.contains("Object.defineProperty(window, 'XMLHttpRequest', {value: ConsoleXMLHttpRequest")
        )
        assertLiveVideoDecoderGuard(socketScript)

        val liveHtml = "<!DOCTYPE html><html><head><title>Console</title><script src=\"/pve2/ext6/pvemanagerlib.js\"></script></head><body><div id=\"noVNC_container\"></div></body></html>"
        LocalConsoleTlsServer("localhost", "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${liveHtml.toByteArray().size}\r\nConnection: close\r\n\r\n$liveHtml").use { server ->
            val transport = ConsoleTransport(
                server.url,
                "test-ticket",
                createConsoleClient("localhost", false, null, server.trustManager),
                socketScript
            )
            val result = transport.fetch(server.url, "GET", emptyMap())
            assertEquals(200, result.status)
            assertEquals("text/html", result.mime)
            val body = result.body.toString(Charsets.UTF_8)
            val scriptTag = "<script>$socketScript</script>"
            assertTrue("Live HTML must contain injected bootstrap script", body.contains(scriptTag))
            val metaTagIndex = body.indexOf("<meta http-equiv=\"Content-Security-Policy\"")
            val bootstrapIndex = body.indexOf(scriptTag)
            val doctypeEnd = body.indexOf("<!DOCTYPE html>") + "<!DOCTYPE html>".length
            assertTrue("Meta tag must be after doctype", metaTagIndex >= doctypeEnd)
            assertTrue("Meta tag must precede bootstrap script", metaTagIndex in 0 until bootstrapIndex)
            val metaTag = body.substring(metaTagIndex, bootstrapIndex)
            assertTrue("Meta CSP must contain worker-src 'none'", metaTag.contains("worker-src 'none'"))
            assertTrue("Meta CSP must contain connect-src 'self'", metaTag.contains("connect-src 'self'"))
            val serverScriptIndex = body.indexOf("<script src=\"/pve2/ext6/pvemanagerlib.js\">")
            assertTrue("Bootstrap must be injected before any server script", bootstrapIndex in 0 until serverScriptIndex)
            assertTrue("Bootstrap must be injected after doctype", body.indexOf("<!DOCTYPE html>") < bootstrapIndex)
            assertTrue("CSP must disable native workers", result.headers["Content-Security-Policy"]?.contains("worker-src 'none'") == true)
            assertTrue("CSP must restrict connect-src", result.headers["Content-Security-Policy"]?.contains("connect-src 'self'") == true)
        }
    }

    private fun assertLiveVideoDecoderGuard(socketScript: String) {
        val stripped = socketScript
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//.*"""), "")

        assertTrue(
            "console-websocket.js must contain a live 'delete window.VideoDecoder;'",
            stripped.contains("delete window.VideoDecoder;")
        )
        val tryCatchRegex = Regex("""\btry\s*\{[^}]*\bdelete\s+window\.VideoDecoder;[^}]*\}\s*catch\b""")
        assertTrue(
            "delete window.VideoDecoder; must sit in a try { ... } catch block",
            tryCatchRegex.containsMatchIn(stripped)
        )
        val deleteIndex = stripped.indexOf("delete window.VideoDecoder;")
        val bridgeIndex = stripped.indexOf("window.PXMXConsoleSocket")
        assertTrue(
            "window.PXMXConsoleSocket bridge must be found in console-websocket.js",
            bridgeIndex != -1
        )
        assertTrue(
            "delete window.VideoDecoder; must appear before window.PXMXConsoleSocket",
            deleteIndex in 0 until bridgeIndex
        )
    }
}
