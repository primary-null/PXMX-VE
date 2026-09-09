package com.pxmx.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun formatBytes_nullAndTiny() {
        assertEquals("—", formatBytes(null))
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun formatBytes_powersOf1024() {
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.5 KiB", formatBytes(1536))
        assertEquals("1.0 MiB", formatBytes(1024L * 1024L))
        assertEquals("1.0 GiB", formatBytes(1024L * 1024L * 1024L))
        assertEquals("1.0 TiB", formatBytes(1024L * 1024L * 1024L * 1024L))
    }

    @Test
    fun formatUptime_nullAndZero() {
        assertEquals("—", formatUptime(null))
        assertEquals("—", formatUptime(0))
        assertEquals("—", formatUptime(-1))
    }

    @Test
    fun formatUptime_components() {
        assertEquals("5m", formatUptime(5 * 60))
        assertEquals("2h 5m", formatUptime(2 * 3600 + 5 * 60))
        assertEquals("1d 2h 5m", formatUptime(86400 + 2 * 3600 + 5 * 60))
    }

    @Test
    fun formatPercent() {
        assertEquals("—", formatPercent(null))
        assertEquals("0.0%", formatPercent(0.0))
        assertEquals("12.3%", formatPercent(12.34))
        assertEquals("100.0%", formatPercent(100.0))
    }

    @Test
    fun formatEpoch() {
        assertEquals("—", formatEpoch(null))
        assertEquals("—", formatEpoch(0))
        val stamped = formatEpoch(1_700_000_000)
        assertTrue(stamped.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
    }

    @Test
    fun formatLastLoginMs() {
        assertEquals("never", formatLastLoginMs(null))
        assertEquals("never", formatLastLoginMs(0))
        val now = System.currentTimeMillis()
        assertTrue(formatLastLoginMs(now).startsWith("just now"))
        assertTrue(formatLastLoginMs(now - 5 * 60_000).contains("5m ago"))
        assertTrue(formatLastLoginMs(now - 3 * 3_600_000).contains("3h ago"))
    }

    @Test
    fun formatMemoryMiB() {
        assertEquals("—", formatMemoryMiB(null))
        assertEquals("—", formatMemoryMiB(""))
        assertEquals("—", formatMemoryMiB("  "))
        assertEquals("not-a-number", formatMemoryMiB("not-a-number"))
        assertEquals("1.0 GiB", formatMemoryMiB("1024"))
        assertEquals("512.0 MiB", formatMemoryMiB("512"))
    }
}
