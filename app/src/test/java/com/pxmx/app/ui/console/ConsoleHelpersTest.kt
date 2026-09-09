package com.pxmx.app.ui.console

import com.pxmx.app.data.console.ConsoleUrlBuilder
import com.pxmx.app.data.model.GuestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ConsoleHelpersTest {

    @Test
    fun testBuildCookieHostUrl() {
        assertEquals("https://pve.lan:8006", ConsoleUrlBuilder.buildCookieHostUrl("pve.lan:8006"))
        assertEquals("https://pve.lan:8006", ConsoleUrlBuilder.buildCookieHostUrl("https://pve.lan:8006"))
        assertEquals("https://pve.lan:8006", ConsoleUrlBuilder.buildCookieHostUrl("http://pve.lan:8006/"))
        assertEquals("https://demo", ConsoleUrlBuilder.buildCookieHostUrl("demo"))
    }

    @Test
    fun testResolveConsoleKind() {
        assertEquals("kvm", ConsoleUrlBuilder.resolveConsoleKind(GuestType.QEMU))
        assertEquals("lxc", ConsoleUrlBuilder.resolveConsoleKind(GuestType.LXC))
        assertEquals("shell", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE))
        assertEquals("shell", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE, cmd = null))
        assertEquals("upgrade", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE, cmd = "upgrade"))
        assertEquals("login", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE, cmd = "login"))
    }

    @Test
    fun testResolveUiParam() {
        assertEquals("novnc=1", ConsoleUrlBuilder.resolveUiParam(GuestType.QEMU))
        assertEquals("novnc=1", ConsoleUrlBuilder.resolveUiParam(GuestType.LXC))
        assertEquals("xtermjs=1", ConsoleUrlBuilder.resolveUiParam(GuestType.NODE))
    }

    @Test
    fun testQemuNovncUrlBuilding() {
        val session = ConsoleUrlBuilder.buildSession(
            hostPort = "192.168.1.100:8006",
            authCookie = "PVE:root@pam:TICKET",
            node = "pve1",
            guestType = GuestType.QEMU,
            vmid = 100L,
            port = "5900",
            vncticket = "TICKET+VAL/123=",
            name = "ubuntu-vm",
        )

        assertEquals("https://192.168.1.100:8006", session.cookieHostUrl)
        assertEquals("PVE:root@pam:TICKET", session.pveAuthCookie)
        assertEquals(GuestType.QEMU, session.guestType)
        assertEquals("pve1", session.node)
        assertEquals(100L, session.vmid)
        assertEquals("ubuntu-vm", session.name)

        val url = session.pageUrl
        assertTrue(url.startsWith("https://192.168.1.100:8006/?"))
        assertTrue(url.contains("console=kvm"))
        assertTrue(url.contains("novnc=1"))
        assertFalse(url.contains("xtermjs=1"))
        assertTrue(url.contains("vmid=100"))
        assertTrue(url.contains("node=pve1"))
        assertTrue(url.contains("resize=scale"))

        val pathParam = url.substringAfter("path=")
        val decodedPath = URLDecoder.decode(pathParam, StandardCharsets.UTF_8.toString())
        assertTrue(decodedPath.startsWith("api2/json/nodes/pve1/qemu/100/vncwebsocket?"))
        assertTrue(decodedPath.contains("port=5900"))
        assertTrue(decodedPath.contains("vncticket=TICKET%2BVAL%2F123%3D"))
    }

    @Test
    fun testLxcNovncUrlBuilding() {
        val session = ConsoleUrlBuilder.buildSession(
            hostPort = "10.0.0.50:8006",
            authCookie = "COOKIE_LXC",
            node = "node-alpha",
            guestType = GuestType.LXC,
            vmid = 200L,
            port = "5901",
            vncticket = "TICKET_LXC",
            name = "debian-ct",
        )

        assertEquals(GuestType.LXC, session.guestType)
        val url = session.pageUrl
        assertTrue(url.contains("console=lxc"))
        assertTrue(url.contains("novnc=1"))
        assertFalse(url.contains("xtermjs=1"))
        assertTrue(url.contains("vmid=200"))
        assertTrue(url.contains("node=node-alpha"))

        val pathParam = url.substringAfter("path=")
        val decodedPath = URLDecoder.decode(pathParam, StandardCharsets.UTF_8.toString())
        assertTrue(decodedPath.startsWith("api2/json/nodes/node-alpha/lxc/200/vncwebsocket?"))
        assertTrue(decodedPath.contains("port=5901"))
        assertTrue(decodedPath.contains("vncticket=TICKET_LXC"))
    }

    @Test
    fun testNodeXtermjsUrlBuilding() {
        val session = ConsoleUrlBuilder.buildSession(
            hostPort = "pve.cluster.local:8006",
            authCookie = "COOKIE_NODE",
            node = "node-main",
            guestType = GuestType.NODE,
            vmid = 0L,
            port = "5902",
            vncticket = "TICKET_NODE",
            name = "node-main",
            cmd = null,
        )

        assertEquals(GuestType.NODE, session.guestType)
        val url = session.pageUrl
        assertTrue(url.contains("console=shell"))
        assertTrue(url.contains("xtermjs=1"))
        assertFalse(url.contains("novnc=1"))
        assertTrue(url.contains("node=node-main"))

        val pathParam = url.substringAfter("path=")
        val decodedPath = URLDecoder.decode(pathParam, StandardCharsets.UTF_8.toString())
        // Node shell websocket path does not include guestType or vmid
        assertTrue(decodedPath.startsWith("api2/json/nodes/node-main/vncwebsocket?"))
        assertFalse(decodedPath.contains("/qemu/"))
        assertFalse(decodedPath.contains("/lxc/"))
        assertTrue(decodedPath.contains("port=5902"))
        assertTrue(decodedPath.contains("vncticket=TICKET_NODE"))
    }

    @Test
    fun testNodeXtermjsUpgradeAndLoginCommands() {
        val upgradeSession = ConsoleUrlBuilder.buildSession(
            hostPort = "pve.cluster.local:8006",
            authCookie = "COOKIE",
            node = "node-main",
            guestType = GuestType.NODE,
            vmid = 0L,
            port = "5903",
            vncticket = "TICKET",
            name = "node-main",
            cmd = "upgrade",
        )
        assertTrue(upgradeSession.pageUrl.contains("console=upgrade"))
        assertTrue(upgradeSession.pageUrl.contains("xtermjs=1"))

        val loginSession = ConsoleUrlBuilder.buildSession(
            hostPort = "pve.cluster.local:8006",
            authCookie = "COOKIE",
            node = "node-main",
            guestType = GuestType.NODE,
            vmid = 0L,
            port = "5904",
            vncticket = "TICKET",
            name = "node-main",
            cmd = "login",
        )
        assertTrue(loginSession.pageUrl.contains("console=login"))
        assertTrue(loginSession.pageUrl.contains("xtermjs=1"))
    }

    @Test
    fun testDemoModeLoadsDemoShellHtml() {
        val session = ConsoleUrlBuilder.buildSession(
            hostPort = "demo",
            authCookie = "DEMO_TICKET",
            node = "alpha",
            guestType = GuestType.NODE,
            vmid = 0L,
            port = "0",
            vncticket = "DEMO",
            name = "alpha",
            isDemo = true,
        )

        assertTrue(session.pageUrl.startsWith("data:text/html;charset=utf-8,"))
        assertEquals("https://demo", session.cookieHostUrl)
        val decodedHtml = URLDecoder.decode(
            session.pageUrl.removePrefix("data:text/html;charset=utf-8,"),
            StandardCharsets.UTF_8.toString(),
        )
        assertTrue(decodedHtml.contains("Welcome to Proxmox VE Terminal (Demo Session)"))
        assertTrue(decodedHtml.contains("root@alpha:~#"))
    }

    @Test
    fun testCoerceMimeForJsFiles() {
        // Missing or generic MIME types on JS/ES module files must be coerced to application/javascript
        assertEquals(
            "application/javascript",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/novnc/app.js?ver=1.7.0-2", null),
        )
        assertEquals(
            "application/javascript",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/novnc/core/rfb.js", "application/octet-stream"),
        )
        assertEquals(
            "application/javascript",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/novnc/core/util/strings.js", "text/plain; charset=utf-8"),
        )
        assertEquals(
            "application/javascript",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/xtermjs/xterm.js", ""),
        )
        assertEquals(
            "application/javascript",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/xtermjs/addons/fit/fit.js", "text/x-javascript"),
        )
        assertEquals(
            "application/javascript",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/novnc/app.js", "text/javascript"),
        )
        assertEquals(
            "application/javascript",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/module.mjs", "application/octet-stream"),
        )
    }

    @Test
    fun testCoerceMimeForNonJsFiles() {
        assertEquals(
            "text/css",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/pve2/css/ext6-pve.css", "text/plain"),
        )
        assertEquals(
            "text/html",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/?console=kvm&novnc=1", "text/html; charset=UTF-8"),
        )
        assertEquals(
            "image/png",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/pve2/images/logo-128.png", "image/png"),
        )
        assertEquals(
            "application/json",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/api2/json/version", "application/json"),
        )
        assertEquals(
            "application/wasm",
            ConsoleMimeUtils.coerceMimeType("https://pve:8006/wasm/engine.wasm", "application/octet-stream"),
        )
    }

    @Test
    fun testExtractCharset() {
        assertEquals("UTF-8", ConsoleMimeUtils.extractCharset("text/html; charset=UTF-8"))
        assertEquals("utf-8", ConsoleMimeUtils.extractCharset("application/javascript; charset=utf-8"))
        assertEquals("iso-8859-1", ConsoleMimeUtils.extractCharset("text/plain; charset=\"iso-8859-1\""))
        assertEquals(null, ConsoleMimeUtils.extractCharset("application/javascript"))
        assertEquals(null, ConsoleMimeUtils.extractCharset(null))
    }

    @Test
    fun testBuildResponseHeaders() {
        val rawHeaders = listOf(
            "Content-Type" to "text/plain",
            "Content-Length" to "4096",
            "Content-Encoding" to "gzip",
            "Transfer-Encoding" to "chunked",
            "Cache-Control" to "public, max-age=3600",
            "X-PVE-Version" to "8.3.0",
        )

        val headers = ConsoleMimeUtils.buildResponseHeaders(
            rawHeaders = rawHeaders,
            mime = "application/javascript",
            encoding = "utf-8",
        )

        assertEquals("application/javascript; charset=utf-8", headers["Content-Type"])
        assertEquals("public, max-age=3600", headers["Cache-Control"])
        assertEquals("8.3.0", headers["X-PVE-Version"])
        assertFalse(headers.containsKey("Content-Encoding"))
        assertFalse(headers.containsKey("Content-Length"))
        assertFalse(headers.containsKey("Transfer-Encoding"))
    }
}
