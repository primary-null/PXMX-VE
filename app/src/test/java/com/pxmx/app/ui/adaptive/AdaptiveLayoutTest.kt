package com.pxmx.app.ui.adaptive

import com.pxmx.app.data.console.ConsoleUrlBuilder
import com.pxmx.app.data.model.GuestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun testOperatorTwoPaneWidthBreakpoints() {
        assertFalse("widthDp 320 should not be two-pane (cover)", isOperatorTwoPane(320))
        assertTrue("widthDp 600 should be two-pane (inner portrait Medium lower bound)", isOperatorTwoPane(600))
        assertTrue("widthDp 800 should be two-pane (inner portrait Medium)", isOperatorTwoPane(800))
        assertTrue("widthDp 840 should be two-pane (Expanded)", isOperatorTwoPane(840))
    }

    @Test
    fun testLoginTwoPaneWidthBreakpoints() {
        assertFalse("widthDp 320: not two-pane (cover login stacked)", isOperatorTwoPane(320))
        assertTrue("widthDp 600: two-pane (inner login split)", isOperatorTwoPane(600))
        assertTrue("widthDp 800: two-pane (inner login split)", isOperatorTwoPane(800))
    }

    @Test
    fun testIsWideViewport() {
        assertFalse("1768x2208 should not be wide (tall portrait)", isWideViewport(1768, 2208))
        assertTrue("2208x1768 should be wide (landscape)", isWideViewport(2208, 1768))
    }

    @Test
    fun testIsTabletop() {
        assertTrue(
            "HALF_OPENED + HORIZONTAL should be tabletop",
            isTabletop("HALF_OPENED", "HORIZONTAL"),
        )
        assertFalse(
            "FLAT + HORIZONTAL should not be tabletop",
            isTabletop("FLAT", "HORIZONTAL"),
        )
        assertFalse(
            "HALF_OPENED + VERTICAL should not be tabletop",
            isTabletop("HALF_OPENED", "VERTICAL"),
        )
    }

    @Test
    fun testTabletopIntentConsoleOpenKeepsConsole() {
        assertEquals(
            TabletopIntent.KEEP_CONSOLE,
            tabletopIntent(isTabletop = true, consoleOpen = true, consoleIsNode = false),
        )
        assertEquals(
            TabletopIntent.KEEP_CONSOLE,
            tabletopIntent(isTabletop = true, consoleOpen = true, consoleIsNode = true),
        )
    }

    @Test
    fun testTabletopIntentNoConsoleOffersNodeShell() {
        assertEquals(
            TabletopIntent.OFFER_NODE_SHELL,
            tabletopIntent(isTabletop = true, consoleOpen = false, consoleIsNode = false),
        )
        assertEquals(
            TabletopIntent.OFFER_NODE_SHELL,
            tabletopIntent(isTabletop = true, consoleOpen = false, consoleIsNode = true),
        )
    }

    @Test
    fun testTabletopIntentNotTabletopReturnsNone() {
        assertEquals(
            TabletopIntent.NONE,
            tabletopIntent(isTabletop = false, consoleOpen = false, consoleIsNode = false),
        )
        assertEquals(
            TabletopIntent.NONE,
            tabletopIntent(isTabletop = false, consoleOpen = true, consoleIsNode = false),
        )
        assertEquals(
            TabletopIntent.NONE,
            tabletopIntent(isTabletop = false, consoleOpen = true, consoleIsNode = true),
        )
    }

    @Test
    fun testParseSettingsPaneSelection() {
        assertEquals(SettingsPaneSelection.NETWORK, parseSettingsPaneSelection("network"))
        assertEquals(SettingsPaneSelection.NETWORK, parseSettingsPaneSelection("NETWORK"))
        assertEquals(SettingsPaneSelection.SDN, parseSettingsPaneSelection("sdn"))
        assertEquals(SettingsPaneSelection.SDN, parseSettingsPaneSelection("SDN"))
        assertEquals(SettingsPaneSelection.FIREWALL, parseSettingsPaneSelection("firewall"))
        assertEquals(SettingsPaneSelection.FIREWALL, parseSettingsPaneSelection("FIREWALL"))
        assertEquals(SettingsPaneSelection.UPDATES, parseSettingsPaneSelection("updates"))
        assertEquals(SettingsPaneSelection.UPDATES, parseSettingsPaneSelection("UPDATES"))
        assertEquals(SettingsPaneSelection.LOG, parseSettingsPaneSelection("log"))
        assertEquals(SettingsPaneSelection.LOG, parseSettingsPaneSelection("LOG"))
        assertNull(parseSettingsPaneSelection("permissions"))
        assertNull(parseSettingsPaneSelection("invalid"))
        assertNull(parseSettingsPaneSelection(null))
    }

    @Test
    fun testFormatLogPriority() {
        assertEquals("EMERG", formatLogPriority(0))
        assertEquals("ALERT", formatLogPriority(1))
        assertEquals("CRIT", formatLogPriority(2))
        assertEquals("ERR", formatLogPriority(3))
        assertEquals("WARNING", formatLogPriority(4))
        assertEquals("NOTICE", formatLogPriority(5))
        assertEquals("INFO", formatLogPriority(6))
        assertEquals("DEBUG", formatLogPriority(7))
        assertEquals("UNKNOWN", formatLogPriority(null))
        assertEquals("PRI 8", formatLogPriority(8))
        assertEquals("PRI 42", formatLogPriority(42))
    }

    @Test
    fun testConsoleUrlBuilderNodeStillXtermjsAndKindShell() {
        assertEquals("shell", ConsoleUrlBuilder.resolveConsoleKind(GuestType.NODE))
        assertEquals("xtermjs=1", ConsoleUrlBuilder.resolveUiParam(GuestType.NODE))

        val session = ConsoleUrlBuilder.buildSession(
            hostPort = "pve.test.lan:8006",
            authCookie = "COOKIE",
            node = "pve1",
            guestType = GuestType.NODE,
            vmid = 0L,
            port = "5900",
            vncticket = "TICKET",
            name = "pve1",
            cmd = null,
        )
        assertTrue(session.pageUrl.contains("console=shell"))
        assertTrue(session.pageUrl.contains("xtermjs=1"))
    }
}
