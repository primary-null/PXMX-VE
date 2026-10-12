package com.pxmx.app.ui.components

import com.pxmx.app.data.net.PingBand
import org.junit.Assert.assertEquals
import org.junit.Test

class PingFillTest {

    @Test
    fun pingFill_pureMath_verifiesBandsAndFractions() {
        // 8ms GOOD floor fill (0.05)
        assertEquals(PingBand.GOOD, pingBand(8L))
        assertEquals(0.05f, fillFraction(8L), 0.0001f)

        // 12ms GOOD, 12/200 = 0.06, no longer at floor or sharing 0.08
        assertEquals(PingBand.GOOD, pingBand(12L))
        assertEquals(0.06f, fillFraction(12L), 0.0001f)
        org.junit.Assert.assertTrue(fillFraction(8L) != fillFraction(12L))

        // Measured LAN latency checks
        assertEquals(PingBand.GOOD, pingBand(18L))
        assertEquals(0.09f, fillFraction(18L), 0.0001f)
        org.junit.Assert.assertTrue(fillFraction(18L) > 0.05f)

        assertEquals(PingBand.GOOD, pingBand(24L))
        assertEquals(0.12f, fillFraction(24L), 0.0001f)

        assertEquals(PingBand.GOOD, pingBand(28L))
        assertEquals(0.14f, fillFraction(28L), 0.0001f)

        assertEquals(PingBand.FAIR, pingBand(41L))
        assertEquals(0.205f, fillFraction(41L), 0.0001f)
        org.junit.Assert.assertTrue(fillFraction(18L) != fillFraction(41L))

        // 80ms SLOW (80/200 = 0.40)
        assertEquals(PingBand.SLOW, pingBand(80L))
        assertEquals(0.40f, fillFraction(80L), 0.0001f)

        // 90ms SLOW (90/200 = 0.45)
        assertEquals(PingBand.SLOW, pingBand(90L))
        assertEquals(0.45f, fillFraction(90L), 0.001f)

        // 200ms BAD full
        assertEquals(PingBand.BAD, pingBand(200L))
        assertEquals(1f, fillFraction(200L), 0.0001f)

        // 800ms BAD full
        assertEquals(PingBand.BAD, pingBand(800L))
        assertEquals(1f, fillFraction(800L), 0.0001f)

        // 1500ms BAD clamped full
        assertEquals(PingBand.BAD, pingBand(1500L))
        assertEquals(1f, fillFraction(1500L), 0.0001f)

        // 3000ms still BAD full
        assertEquals(PingBand.BAD, pingBand(3000L))
        assertEquals(1f, fillFraction(3000L), 0.0001f)
    }
}
