package com.pxmx.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMastheadTest {

    @Test
    fun narrowLineDropsHostAndKeepsSiteKindAndVersion() {
        val line = homeMastheadLine(
            siteTitle = "lab",
            host = "example.test:8006",
            version = "9.2",
            kind = "Standalone",
            includeHost = false,
        )
        assertEquals("lab · Standalone · PVE 9.2", line)
        assertFalse(line.contains("example.test"))
    }

    @Test
    fun wideLineKeepsHostBetweenSiteAndVersion() {
        val line = homeMastheadLine(
            siteTitle = "lab",
            host = "example.test:8006",
            version = "9.2",
            kind = "3 nodes",
            includeHost = true,
        )
        assertEquals("lab · example.test:8006 · PVE 9.2 · 3 nodes", line)
    }

    @Test
    fun blankHostIsOmittedEvenWhenWide() {
        val line = homeMastheadLine("lab", "—", "9.2", "Standalone", includeHost = true)
        assertEquals("lab · Standalone · PVE 9.2", line)
    }

    @Test
    fun hostPreferenceFollowsPaneWidth() {
        assertFalse(homeMastheadPrefersHost(360f))
        assertFalse(homeMastheadPrefersHost(520f))
        assertTrue(homeMastheadPrefersHost(640f))
    }

    @Test
    fun wordmarkShrinksOnANarrowPaneAndOnScroll() {
        assertEquals(24f, homeWordmarkSp(300f, 0f), 0.01f)
        assertEquals(28f, homeWordmarkSp(400f, 0f), 0.01f)
        assertEquals(34f, homeWordmarkSp(800f, 0f), 0.01f)
        assertEquals(20f, homeWordmarkSp(800f, 1f), 0.01f)
    }
}
