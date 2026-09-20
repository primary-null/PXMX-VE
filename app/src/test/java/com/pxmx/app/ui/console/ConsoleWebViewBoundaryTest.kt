package com.pxmx.app.ui.console

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Wiring guards complement the real TLS/socket tests; these are not Android instrumentation. */
class ConsoleWebViewBoundaryTest {
    @Test
    fun `WebView cannot fall back to native network or SSL proceed`() {
        val source = File("src/main/java/com/pxmx/app/ui/console/ConsoleScreen.kt").readText()
        assertTrue("WebSocket and other non-intercepted native loads must be disabled", source.contains("settings.blockNetworkLoads = true"))
        assertFalse("Never approve native TLS outside the pinned client", source.contains("handler?.proceed()"))
        assertFalse("Keep authentication cookies out of the native network stack", source.contains("PVEAuthCookie=${'$'}{session.pveAuthCookie}"))
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
}
