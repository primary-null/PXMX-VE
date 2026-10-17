package com.pxmx.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanVizTest {

    @Test
    fun scanViz_mode_defaultsToScanPicture() {
        assertEquals(ScanVizMode.SCAN_PICTURE, ScanViz.mode)
    }

    @Test
    fun scanAddressBusMath_isSingleSlash24_detects254Addresses() {
        assertTrue(ScanAddressBusMath.isSingleSlash24(254))
        assertFalse(ScanAddressBusMath.isSingleSlash24(255))
        assertFalse(ScanAddressBusMath.isSingleSlash24(253))
        assertFalse(ScanAddressBusMath.isSingleSlash24(64))
        assertFalse(ScanAddressBusMath.isSingleSlash24(1024))
        assertFalse(ScanAddressBusMath.isSingleSlash24(0))
    }

    @Test
    fun scanAddressBusMath_parseLastOctet_validatesDottedQuads() {
        assertEquals(1, ScanAddressBusMath.parseLastOctet("192.168.1.1"))
        assertEquals(42, ScanAddressBusMath.parseLastOctet("10.0.0.42"))
        assertEquals(254, ScanAddressBusMath.parseLastOctet("172.16.0.254"))

        // Out of host bounds or non-quads
        assertNull(ScanAddressBusMath.parseLastOctet("192.168.1.0"))
        assertNull(ScanAddressBusMath.parseLastOctet("192.168.1.255"))
        assertNull(ScanAddressBusMath.parseLastOctet("pve.internal"))
        assertNull(ScanAddressBusMath.parseLastOctet("fe80::1"))
        assertNull(ScanAddressBusMath.parseLastOctet(""))
    }

    @Test
    fun scanAddressBusMath_extractLastOctet_onlyExtractsForSlash24() {
        // Single /24: extracts successfully
        assertEquals(55, ScanAddressBusMath.extractLastOctet("192.168.1.55", totalCount = 254))

        // Non-/24: returns null even if dotted quad format
        assertNull(ScanAddressBusMath.extractLastOctet("192.168.1.55", totalCount = 64))
        assertNull(ScanAddressBusMath.extractLastOctet("192.168.1.55", totalCount = 510))
    }

    @Test
    fun pipFraction_slash24_mapsByProgressOrIp() {
        val totalCount = 254

        // Progress based
        assertEquals(0f, ScanAddressBusMath.pipFraction(0, totalCount), 0.001f)
        assertEquals(0.5f, ScanAddressBusMath.pipFraction(127, totalCount), 0.001f)
        assertEquals(1f, ScanAddressBusMath.pipFraction(254, totalCount), 0.001f)

        // IP based on dotted quad in /24
        assertEquals(0f, ScanAddressBusMath.pipFraction(50, totalCount, currentIp = "192.168.1.1"), 0.001f)
        assertEquals(1f, ScanAddressBusMath.pipFraction(50, totalCount, currentIp = "192.168.1.254"), 0.001f)
    }

    @Test
    fun pipFraction_nonSlash24_fallsBackToSweepFraction() {
        // When totalCount is not 254, MUST NOT parse last octet and must fall back to sweep-fraction
        val totalCount = 50

        // Scanned 25 of 50 is 0.5f, even if current IP has last octet 1
        val fraction = ScanAddressBusMath.pipFraction(
            scannedCount = 25,
            totalCount = totalCount,
            currentIp = "10.0.0.1",
        )
        assertEquals(0.5f, fraction, 0.0001f)

        // Zero totalCount returns 0f safely
        assertEquals(0f, ScanAddressBusMath.pipFraction(10, 0), 0.0001f)
    }

    @Test
    fun beaconFraction_slash24_dottedQuadUsesLastOctet() {
        val totalCount = 254

        assertEquals(0f, ScanAddressBusMath.beaconFraction("192.168.1.1", totalCount), 0.001f)
        assertEquals(1f, ScanAddressBusMath.beaconFraction("192.168.1.254", totalCount), 0.001f)
    }

    @Test
    fun busLabel_cidrStays_hostIpBecomesPrefix() {
        assertEquals("BUS", ScanAddressBusMath.busLabel(null, 254))
        assertEquals("BUS", ScanAddressBusMath.busLabel("  ", 254))
        assertEquals("192.0.2.0/24", ScanAddressBusMath.busLabel("192.0.2.0/24", 254))
        assertEquals("10.0.0.0/24", ScanAddressBusMath.busLabel("10.0.0.47", 254))
        assertEquals("10.0.0.47", ScanAddressBusMath.busLabel("10.0.0.47", 64))
    }

    @Test
    fun beaconFraction_nonSlash24OrNonDottedQuad_fallsBack() {
        // Non-/24 subnet: does not use octet; uses fallback index proportionally
        val nonSlash24Total = 100
        val frac = ScanAddressBusMath.beaconFraction("192.168.1.254", nonSlash24Total, fallbackIndex = 25)
        assertEquals(0.25f, frac, 0.0001f)

        // Non-dotted quad in /24: uses fallback index
        val nonQuadFrac = ScanAddressBusMath.beaconFraction("pve-node.lan", totalCount = 254, fallbackIndex = 127)
        assertEquals(0.5f, nonQuadFrac, 0.001f)
    }
}
