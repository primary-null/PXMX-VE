package com.pxmx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(1024000L, point.netin)
        assertEquals(2048000L, point.netout)
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
        // disk and maxdisk must be null, NOT 0L, so that missing disk is an empty series or gap, not 0%
        assertNull(point.disk)
        assertNull(point.maxdisk)
    }

    @Test
    fun testNumericCoercion() {
        val map = mapOf(
            "time" to 1700000120L,
            "cpu" to 0.42,
            "mem" to 1000,
            "maxmem" to 2000L,
            "netin" to 100,
            "netout" to 200L,
        )
        val point = RrdPoint.fromMap(map)
        assertEquals(1700000120L, point.time)
        assertEquals(0.42, point.cpu ?: 0.0, 0.0001)
        assertEquals(1000L, point.mem)
        assertEquals(2000L, point.maxmem)
        assertEquals(100L, point.netin)
        assertEquals(200L, point.netout)
    }
}
