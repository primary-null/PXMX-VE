package com.pxmx.app.data.console

import com.pxmx.app.data.model.GuestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleUrlBuilderTest {

    @Test
    fun buildCookieHostUrl_handlesProtocolsAndPorts() {
        assertEquals("https://192.168.1.10:8006", ConsoleUrlBuilder.buildCookieHostUrl("192.168.1.10:8006"))
        assertEquals("https://192.168.1.10:8006", ConsoleUrlBuilder.buildCookieHostUrl("https://192.168.1.10:8006"))
        assertEquals("https://192.168.1.10:8006", ConsoleUrlBuilder.buildCookieHostUrl("http://192.168.1.10:8006/"))
        assertEquals("https://pve.example.com:8006", ConsoleUrlBuilder.buildCookieHostUrl("  https://pve.example.com:8006/  "))
    }

    @Test
    fun resolveConsoleKind_mapsCorrectly() {
        assertEquals("shell", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE))
        assertEquals("upgrade", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE, "upgrade"))
        assertEquals("login", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE, "login"))
        assertEquals("kvm", ConsoleUrlBuilder.resolveConsoleKind(GuestType.QEMU))
        assertEquals("lxc", ConsoleUrlBuilder.resolveConsoleKind(GuestType.LXC))
    }

    @Test
    fun resolveUiParam_mapsCorrectly() {
        assertEquals("xtermjs=1", ConsoleUrlBuilder.resolveUiParam(GuestType.NODE))
        assertEquals("novnc=1", ConsoleUrlBuilder.resolveUiParam(GuestType.QEMU))
        assertEquals("novnc=1", ConsoleUrlBuilder.resolveUiParam(GuestType.LXC))
    }

    @Test
    fun buildRawWebSocketPath_node_and_guest() {
        val nodePath = ConsoleUrlBuilder.buildRawWebSocketPath(
            node = "pve1",
            guestType = GuestType.NODE,
            vmid = 0L,
            port = "5900",
            vncticket = "TICKET+ABC/123==",
        )
        assertEquals("api2/json/nodes/pve1/vncwebsocket?port=5900&vncticket=TICKET%2BABC%2F123%3D%3D", nodePath)

        val qemuPath = ConsoleUrlBuilder.buildRawWebSocketPath(
            node = "pve1",
            guestType = GuestType.QEMU,
            vmid = 100L,
            port = "5900",
            vncticket = "TICKET",
        )
        assertEquals("api2/json/nodes/pve1/qemu/100/vncwebsocket?port=5900&vncticket=TICKET", qemuPath)

        val lxcPath = ConsoleUrlBuilder.buildRawWebSocketPath(
            node = "pve1",
            guestType = GuestType.LXC,
            vmid = 101L,
            port = "5901",
            vncticket = "TICKET",
        )
        assertEquals("api2/json/nodes/pve1/lxc/101/vncwebsocket?port=5901&vncticket=TICKET", lxcPath)
    }

    @Test
    fun buildPageUrl_demoMode_generatesHtml() {
        val demoUrl = ConsoleUrlBuilder.buildPageUrl(
            cookieHostUrl = "https://demo:8006",
            node = "demo",
            guestType = GuestType.QEMU,
            vmid = 100L,
            port = "5900",
            vncticket = "ticket",
            isDemo = true,
            name = "test-vm",
        )
        assertTrue("Demo URL must be a data: text/html URI", demoUrl.startsWith("data:text/html"))
        assertTrue("Demo URL must contain guest name", demoUrl.contains("test-vm") || demoUrl.contains("100"))
    }

    @Test
    fun buildPageUrl_realCluster_containsQueryParameters() {
        val url = ConsoleUrlBuilder.buildPageUrl(
            cookieHostUrl = "https://192.168.1.10:8006",
            node = "node1",
            guestType = GuestType.QEMU,
            vmid = 105L,
            port = "5900",
            vncticket = "TICKET123",
            cmd = null,
            isDemo = false,
            name = "vm105",
        )
        assertTrue(url.startsWith("https://192.168.1.10:8006/?"))
        assertTrue(url.contains("console=kvm"))
        assertTrue(url.contains("novnc=1"))
        assertTrue(url.contains("vmid=105"))
        assertTrue(url.contains("node=node1"))
        assertTrue(url.contains("resize=scale"))
        assertTrue(url.contains("path="))
    }

    @Test
    fun buildSession_constructsValidSessionObject() {
        val session = ConsoleUrlBuilder.buildSession(
            hostPort = "192.168.1.10:8006",
            authCookie = "AUTH_COOKIE_XYZ",
            node = "pve1",
            guestType = GuestType.NODE,
            vmid = 0L,
            port = "5900",
            vncticket = "TICKET",
            name = "pve1",
        )
        assertEquals("https://192.168.1.10:8006", session.cookieHostUrl)
        assertEquals("AUTH_COOKIE_XYZ", session.pveAuthCookie)
        assertEquals(GuestType.NODE, session.guestType)
        assertEquals("pve1", session.node)
        assertEquals(0L, session.vmid)
        assertEquals("pve1", session.name)
        assertTrue(session.pageUrl.contains("console=shell"))
        assertTrue(session.pageUrl.contains("xtermjs=1"))
    }
}
