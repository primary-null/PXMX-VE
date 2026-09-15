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
        assertTrue(source.contains("WebSettings.LOAD_NO_CACHE"))
    }
}
