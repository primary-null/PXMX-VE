package com.pxmx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RrdPointTest {

    @Test
    fun testParseFullRrdPoint() {
        val map = mapOf(
            "time" to 1700000000L,
            "cpu" to 0.25,
            "mem" to 2147483648L,
            "maxmem" to 4294967296L,
            "netin" to 1024000L,
            "netout" to 2048000L,
            "disk" to 10737418240L,
            "maxdisk" to 53687091200L,
        )
        val point = RrdPoint.fromMap(map)
        assertEquals(1700000000L, point.time)
        assertEquals(0.25, point.cpu ?: 0.0, 0.0001)
        assertEquals(2147483648L, point.mem)
        assertEquals(4294967296L, point.maxmem)
        assertEquals(1024000.0, point.netin ?: 0.0, 0.0001)
        assertEquals(2048000.0, point.netout ?: 0.0, 0.0001)
        assertEquals(10737418240L, point.disk)
        assertEquals(53687091200L, point.maxdisk)
    }

    @Test
    fun testMissingDiskProducesNullNotZero() {
        val map = mapOf(
            "time" to 1700000060L,
            "cpu" to 0.15,
            "mem" to 1073741824L,
            "maxmem" to 4294967296L,
            "netin" to 5000L,
            "netout" to 12000L,
        )
        val point = RrdPoint.fromMap(map)
        assertEquals(1700000060L, point.time)
        assertEquals(0.15, point.cpu ?: 0.0, 0.0001)
        assertNull(point.disk)
        assertNull(point.maxdisk)
        assertNull(point.diskread)
        assertNull(point.diskwrite)
        assertNull(point.iowait)
    }

    @Test
    fun testNumericCoercion() {
        val map = mapOf(
            "time" to 1700000120L,
            "cpu" to 0.42,
            "mem" to 1000,
            "maxmem" to 2000L,
            "netin" to 100.7,
            "netout" to 200L,
        )
        val point = RrdPoint.fromMap(map)
        assertEquals(1700000120L, point.time)
        assertEquals(0.42, point.cpu ?: 0.0, 0.0001)
        assertEquals(1000L, point.mem)
        assertEquals(2000L, point.maxmem)
        assertEquals(100.7, point.netin ?: 0.0, 0.0001)
        assertEquals(200.0, point.netout ?: 0.0, 0.0001)
    }

    @Test
    fun nodeKeys_mapMemusedAndRoot() {
        val map = mapOf(
            "time" to 1700000180L,
            "cpu" to 0.11,
            "iowait" to 0.04,
            "memused" to 8_000_000_000L,
            "memtotal" to 32_000_000_000L,
            "rootused" to 12_000_000_000L,
            "roottotal" to 100_000_000_000L,
            "netin" to 12345.6,
            "netout" to 789.1,
        )
        val point = RrdPoint.fromMap(map)
        assertEquals(8_000_000_000L, point.mem)
        assertEquals(32_000_000_000L, point.maxmem)
        assertEquals(12_000_000_000L, point.disk)
        assertEquals(100_000_000_000L, point.maxdisk)
        assertEquals(0.04, point.iowait ?: 0.0, 0.0001)
        assertEquals(12345.6, point.netin ?: 0.0, 0.0001)
    }

    @Test
    fun guestKeys_preferredOverNodeAliases() {
        val map = mapOf(
            "time" to 1L,
            "mem" to 100L,
            "memused" to 1L,
            "maxmem" to 200L,
            "memtotal" to 2L,
            "disk" to 3L,
            "rootused" to 9L,
            "maxdisk" to 4L,
            "roottotal" to 8L,
        )
        val point = RrdPoint.fromMap(map)
        assertEquals(100L, point.mem)
        assertEquals(200L, point.maxmem)
        assertEquals(3L, point.disk)
        assertEquals(4L, point.maxdisk)
    }

    @Test
    fun timeOnlySample_hasNoMetrics() {
        val point = RrdPoint.fromMap(mapOf("time" to 1700000240L))
        assertEquals(1700000240L, point.time)
        assertFalse(point.hasMetrics())
        val kept = listOf(
            RrdPoint.fromMap(mapOf("time" to 1L, "cpu" to 0.2)),
            point,
        ).withoutEmptySamples()
        assertEquals(1, kept.size)
        assertTrue(kept.single().hasMetrics())
    }
}
