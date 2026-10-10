package com.pxmx.app.ui.components

import com.pxmx.app.data.model.RrdPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySeriesTest {

    @Test
    fun cpuPercent_fractionAndAlreadyPercent() {
        assertEquals(12.0, cpuPercent(0.12)!!, 0.0001)
        assertEquals(12.0, cpuPercent(12.0)!!, 0.0001)
        assertNull(cpuPercent(null))
    }

    @Test
    fun memPercent_usesMax() {
        val point = RrdPoint(time = 1, mem = 25, maxmem = 100)
        assertEquals(25.0, memPercent(point)!!, 0.0001)
        assertNull(memPercent(RrdPoint(time = 1, mem = 25)))
        assertNull(memPercent(RrdPoint(time = 1, mem = 25, maxmem = 0)))
    }

    @Test
    fun guestRows_includeDiskIo_notRoot() {
        val points = listOf(
            RrdPoint(
                time = 1,
                cpu = 0.1,
                mem = 1,
                maxmem = 2,
                netin = 10.0,
                netout = 4.0,
                diskread = 0.0,
                diskwrite = 8.0,
            ),
        )
        assertEquals(
            listOf(HistoryRowId.CPU, HistoryRowId.MEM, HistoryRowId.NET, HistoryRowId.DISK_IO),
            historyRows(isGuest = true, points = points),
        )
    }

    @Test
    fun nodeRows_includeRoot_notDiskIo() {
        val points = listOf(
            RrdPoint(
                time = 1,
                cpu = 0.2,
                iowait = 0.01,
                mem = 1,
                maxmem = 4,
                netin = 1.0,
                disk = 10,
                maxdisk = 100,
            ),
        )
        val rows = historyRows(isGuest = false, points = points)
        assertEquals(
            listOf(HistoryRowId.CPU, HistoryRowId.MEM, HistoryRowId.NET, HistoryRowId.ROOT),
            rows,
        )
        assertTrue(HistoryRowId.DISK_IO !in rows)
    }

    @Test
    fun lastPointWith_skipsTrailingGapForThatSeries() {
        val points = listOf(
            RrdPoint(time = 1, cpu = 0.1, diskread = 8.0, diskwrite = 2.0),
            RrdPoint(time = 2, cpu = 0.2),
        )
        assertEquals(1L, lastPointWith(points) { rowHasValue(HistoryRowId.DISK_IO, it) }?.time)
        assertEquals(2L, lastPointWith(points) { rowHasValue(HistoryRowId.CPU, it) }?.time)
    }

    @Test
    fun guestDiskPercent_doesNotCreateDiskIoRow() {
        val points = listOf(
            RrdPoint(time = 1, cpu = 0.1, disk = 0, maxdisk = 32),
        )
        assertEquals(listOf(HistoryRowId.CPU), historyRows(isGuest = true, points = points))
    }
}
