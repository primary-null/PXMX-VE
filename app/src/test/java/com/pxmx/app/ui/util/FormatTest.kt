package com.pxmx.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun formatDiskUsage_nullOrZeroUsedShowsHonestEmDash() {
        assertEquals("— / 64.0 GiB", formatDiskUsage(null, 64L * 1024L * 1024L * 1024L))
        assertEquals("— / 64.0 GiB", formatDiskUsage(0L, 64L * 1024L * 1024L * 1024L))
        assertEquals("— / 64.0 GiB", formatDiskUsage(-1L, 64L * 1024L * 1024L * 1024L))
        assertEquals("— / —", formatDiskUsage(null, null))
        assertEquals("— / —", formatDiskUsage(0L, null))
    }

    @Test
    fun formatDiskUsage_realUsage() {
        assertEquals("16.0 GiB / 64.0 GiB", formatDiskUsage(16L * 1024L * 1024L * 1024L, 64L * 1024L * 1024L * 1024L))
        assertEquals("500 B / 1000 B", formatDiskUsage(500L, 1000L))
    }

    @Test
    fun formatDiskProgress_nullOrZeroUsedReturnsNull() {
        assertNull(formatDiskProgress(null, 64L * 1024L * 1024L * 1024L))
        assertNull(formatDiskProgress(0L, 64L * 1024L * 1024L * 1024L))
        assertNull(formatDiskProgress(-5L, 64L * 1024L * 1024L * 1024L))
        assertNull(formatDiskProgress(100L, null))
        assertNull(formatDiskProgress(100L, 0L))
        assertNull(formatDiskProgress(100L, -10L))
    }

    @Test
    fun formatDiskProgress_realUsageReturnsRatio() {
        assertEquals(0.25f, formatDiskProgress(16L * 1024L * 1024L * 1024L, 64L * 1024L * 1024L * 1024L)!!, 0.001f)
        assertEquals(1.0f, formatDiskProgress(80L * 1024L * 1024L * 1024L, 64L * 1024L * 1024L * 1024L)!!, 0.001f)
    }
}
