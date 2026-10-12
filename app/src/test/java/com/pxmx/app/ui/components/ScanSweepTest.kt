package com.pxmx.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSweepTest {

    @Test
    fun sweepFraction_calculatesCorrectFractionsAndBounds() {
        // Zero or negative totals
        assertEquals(0f, ScanSweepMath.sweepFraction(0, 0), 0.0001f)
        assertEquals(0f, ScanSweepMath.sweepFraction(10, -1), 0.0001f)

        // Standard progression
        assertEquals(0f, ScanSweepMath.sweepFraction(0, 254), 0.0001f)
        assertEquals(0.5f, ScanSweepMath.sweepFraction(127, 254), 0.0001f)
        assertEquals(1.0f, ScanSweepMath.sweepFraction(254, 254), 0.0001f)

        // Clamping bounds
        assertEquals(0f, ScanSweepMath.sweepFraction(-5, 254), 0.0001f)
        assertEquals(1.0f, ScanSweepMath.sweepFraction(300, 254), 0.0001f)
    }

    @Test
    fun filledCellCount_discreteCellMapping() {
        val totalCells = 24

        assertEquals(0, ScanSweepMath.filledCellCount(0, 254, totalCells))
        assertEquals(12, ScanSweepMath.filledCellCount(127, 254, totalCells))
        assertEquals(24, ScanSweepMath.filledCellCount(254, 254, totalCells))

        // Degenerate inputs
        assertEquals(0, ScanSweepMath.filledCellCount(50, 0, totalCells))
        assertEquals(0, ScanSweepMath.filledCellCount(50, 254, 0))
    }

    @Test
    fun activeCellIndex_tracksDuringScan_andHidesWhenStoppedOrDone() {
        val totalCells = 24

        // Active scan in progress
        assertEquals(0, ScanSweepMath.activeCellIndex(0, 254, totalCells, isScanning = true))
        assertEquals(12, ScanSweepMath.activeCellIndex(127, 254, totalCells, isScanning = true))
        assertEquals(23, ScanSweepMath.activeCellIndex(253, 254, totalCells, isScanning = true))

        // Complete scan: no active cell (returns -1)
        assertEquals(-1, ScanSweepMath.activeCellIndex(254, 254, totalCells, isScanning = true))

        // Stopped / halted scan: hides live sweep indicator (-1) so bar does not freeze
        assertEquals(-1, ScanSweepMath.activeCellIndex(127, 254, totalCells, isScanning = false))
        assertEquals(-1, ScanSweepMath.activeCellIndex(0, 254, totalCells, isScanning = false))
    }

    @Test
    fun cellIndexForHost_slash24_mapsByLastOctet() {
        val totalCells = 24
        val totalCount = 254

        // Octet 1 maps to the first cell
        val firstCell = ScanSweepMath.cellIndexForHost("192.168.1.1", totalCount, totalCells)
        assertEquals(0, firstCell)

        // Octet 128 maps near the middle
        val midCell = ScanSweepMath.cellIndexForHost("192.168.1.128", totalCount, totalCells)
        assertEquals(12, midCell)

        // Octet 254 maps to the last cell
        val lastCell = ScanSweepMath.cellIndexForHost("192.168.1.254", totalCount, totalCells)
        assertEquals(23, lastCell)
    }

    @Test
    fun cellIndexForHost_nonSlash24_fallsBackProportionally() {
        val totalCells = 24
        val totalCount = 64 // e.g. /26 subnet

        // Fallback uses fallbackIndex proportionally, does not parse last octet
        val cell0 = ScanSweepMath.cellIndexForHost("10.0.0.128", totalCount, totalCells, fallbackIndex = 0)
        assertEquals(0, cell0)

        val cellMid = ScanSweepMath.cellIndexForHost("10.0.0.128", totalCount, totalCells, fallbackIndex = 32)
        assertEquals(12, cellMid)

        val cellEnd = ScanSweepMath.cellIndexForHost("10.0.0.128", totalCount, totalCells, fallbackIndex = 63)
        assertTrue(cellEnd in 22..23)
    }
}
