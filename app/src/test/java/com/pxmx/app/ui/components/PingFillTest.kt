package com.pxmx.app.ui.components

import com.pxmx.app.data.net.PingBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PingFillTest {

    @Test
    fun pingFill_pureMath_verifiesBandsAndFractions() {
        // 12ms GOOD full-ish
        assertEquals(PingBand.GOOD, pingBand(12L))
        val f12 = fillFraction(12L)
        assertEquals(1f - 12f / 2000f, f12, 0.001f)
        assertTrue(f12 > 0.99f)

        // 90ms FAIR
        assertEquals(PingBand.FAIR, pingBand(90L))
        val f90 = fillFraction(90L)
        assertEquals(1f - 90f / 2000f, f90, 0.001f)
        assertTrue(f90 > 0.95f)

        // 400ms SLOW
        assertEquals(PingBand.SLOW, pingBand(400L))
        val f400 = fillFraction(400L)
        assertEquals(0.8f, f400, 0.001f)

        // 1500ms BAD
        assertEquals(PingBand.BAD, pingBand(1500L))
        val f1500 = fillFraction(1500L)
        assertEquals(0.25f, f1500, 0.001f)

        // 3000ms still BAD with min fill 0.08
        assertEquals(PingBand.BAD, pingBand(3000L))
        val f3000 = fillFraction(3000L)
        assertEquals(0.08f, f3000, 0.0001f)
    }
}
