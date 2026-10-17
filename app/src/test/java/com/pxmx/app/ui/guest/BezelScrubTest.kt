package com.pxmx.app.ui.guest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BezelScrubTest {

    @Test
    fun holdInsideSlopArmsAndAFastSlideYields() {
        assertFalse(bezelScrubShouldArm(heldMs = 239, travelPx = 4f, slopPx = 12f))
        assertTrue(bezelScrubShouldArm(heldMs = 240, travelPx = 11f, slopPx = 12f))
        assertFalse(bezelScrubShouldArm(heldMs = 400, travelPx = 12f, slopPx = 12f))
        assertTrue(bezelScrubShouldYieldToScroll(heldMs = 40, travelPx = 12f, slopPx = 12f))
        assertFalse(bezelScrubShouldYieldToScroll(heldMs = 240, travelPx = 20f, slopPx = 12f))
    }

    @Test
    fun indexUsesTheSpanUnderTheFingerThenTheNearest() {
        val spans = listOf(0f..40f, 44f..80f, 84f..120f)
        assertEquals(0, bezelScrubIndex(10f, spans))
        assertEquals(1, bezelScrubIndex(60f, spans))
        assertEquals(2, bezelScrubIndex(200f, spans))
        assertEquals(0, bezelScrubIndex(-20f, spans))
        assertEquals(0, bezelScrubIndex(10f, emptyList()))
    }

    @Test
    fun bowPeaksOnTheArmedStampAndScaleFlattensWhenReduced() {
        assertEquals(-8f, bezelScrubBowPx(2, armedIndex = 2, peakPx = 8f))
        assertEquals(-4f, bezelScrubBowPx(3, armedIndex = 2, peakPx = 8f))
        assertEquals(0f, bezelScrubBowPx(0, armedIndex = 2, peakPx = 8f))
        assertEquals(0f, bezelScrubBowPx(2, armedIndex = -1, peakPx = 8f))
        assertEquals(1.08f, bezelScrubScale(1, armedIndex = 1, reduceMotion = false))
        assertEquals(1.03f, bezelScrubScale(2, armedIndex = 1, reduceMotion = false))
        assertEquals(1f, bezelScrubScale(1, armedIndex = 1, reduceMotion = true))
    }

    @Test
    fun verticalEscapeCancelsAndASidewaysSlideDoesNot() {
        assertTrue(bezelScrubShouldCancel(dxPx = 4f, dyPx = 25f, escapePx = 24f))
        assertFalse(bezelScrubShouldCancel(dxPx = 30f, dyPx = 10f, escapePx = 24f))
        assertFalse(bezelScrubShouldCancel(dxPx = 8f, dyPx = 24f, escapePx = 24f))
    }
}
