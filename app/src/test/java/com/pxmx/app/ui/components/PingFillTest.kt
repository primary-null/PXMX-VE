package com.pxmx.app.ui.components

import com.pxmx.app.data.net.PingBand
import org.junit.Assert.assertEquals
import org.junit.Test

class PingFillTest {

    @Test
    fun pingFill_pureMath_verifiesBandsAndFractions() {
        // 8ms GOOD floor fill
        assertEquals(PingBand.GOOD, pingBand(8L))
        assertEquals(0.08f, fillFraction(8L), 0.0001f)

        // 12ms GOOD still floor
        assertEquals(PingBand.GOOD, pingBand(12L))
        assertEquals(0.08f, fillFraction(12L), 0.0001f)

        // 90ms FAIR
        assertEquals(PingBand.FAIR, pingBand(90L))
        assertEquals(90f / 800f, fillFraction(90L), 0.001f)

        // 400ms SLOW half plate
        assertEquals(PingBand.SLOW, pingBand(400L))
        assertEquals(0.5f, fillFraction(400L), 0.001f)

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
