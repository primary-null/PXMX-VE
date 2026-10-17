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
    fun warpFocusedTabAndNeighborsWithZeroSanitizationAndReduceMotion() {
        // Focused tab: scale 1.38, shift -14 (toward finger), tilt 10 degrees
        val focused = bezelScrubWarp(index = 2, armedIndex = 2, liftPx = 14f, outwardNextPx = 8f, outwardFarPx = 4f)
        assertEquals(1.38f, focused.scale)
        assertEquals(-14f, focused.translationY)
        assertEquals(0f, focused.translationX)
        assertEquals(10f, focused.rotationZ)

        // Next tabs: shift outward 8, scale 0.90, tilt 0
        val nextRight = bezelScrubWarp(index = 3, armedIndex = 2, liftPx = 14f, outwardNextPx = 8f, outwardFarPx = 4f)
        assertEquals(0.90f, nextRight.scale)
        assertEquals(8f, nextRight.translationX)
        assertEquals(0f, nextRight.translationY)
        assertEquals(0f, nextRight.rotationZ)

        val nextLeft = bezelScrubWarp(index = 1, armedIndex = 2, liftPx = 14f, outwardNextPx = 8f, outwardFarPx = 4f)
        assertEquals(0.90f, nextLeft.scale)
        assertEquals(-8f, nextLeft.translationX)
        assertEquals(0f, nextLeft.translationY)
        assertEquals(0f, nextLeft.rotationZ)

        // Beyond tabs: shift outward 4, scale 0.82, tilt 0
        val farRight = bezelScrubWarp(index = 4, armedIndex = 2, liftPx = 14f, outwardNextPx = 8f, outwardFarPx = 4f)
        assertEquals(0.82f, farRight.scale)
        assertEquals(4f, farRight.translationX)
        assertEquals(0f, farRight.translationY)
        assertEquals(0f, farRight.rotationZ)

        val farLeft = bezelScrubWarp(index = 0, armedIndex = 2, liftPx = 14f, outwardNextPx = 8f, outwardFarPx = 4f)
        assertEquals(0.82f, farLeft.scale)
        assertEquals(-4f, farLeft.translationX)
        assertEquals(0f, farLeft.translationY)
        assertEquals(0f, farLeft.rotationZ)

        // Farther tabs: scale 1, shifts 0, tilt 0
        val farther = bezelScrubWarp(index = 5, armedIndex = 2, liftPx = 14f, outwardNextPx = 8f, outwardFarPx = 4f)
        assertEquals(1f, farther.scale)
        assertEquals(0f, farther.translationX)
        assertEquals(0f, farther.translationY)
        assertEquals(0f, farther.rotationZ)

        // Zero and negative-zero cases the old bow test cared about
        val zeroArmed = bezelScrubWarp(index = 2, armedIndex = -1, liftPx = 14f, outwardNextPx = 8f, outwardFarPx = 4f)
        assertEquals(1f, zeroArmed.scale)
        assertEquals(0f, zeroArmed.translationX)
        assertEquals(0f, zeroArmed.translationY)
        assertEquals(0f, zeroArmed.rotationZ)

        val zeroLift = bezelScrubWarp(index = 2, armedIndex = 2, liftPx = 0f, outwardNextPx = 0f, outwardFarPx = 0f)
        assertEquals(0f, zeroLift.translationY)
        assertEquals(0f, zeroLift.translationX)

        val zeroNext = bezelScrubWarp(index = 1, armedIndex = 2, liftPx = 0f, outwardNextPx = 0f, outwardFarPx = 0f)
        assertEquals(0f, zeroNext.translationX)

        // Reduce motion: drops scale, shift, tilt
        val reduced = bezelScrubWarp(
            index = 2,
            armedIndex = 2,
            reduceMotion = true,
            liftPx = 14f,
            outwardNextPx = 8f,
            outwardFarPx = 4f,
        )
        assertEquals(1f, reduced.scale)
        assertEquals(0f, reduced.translationX)
        assertEquals(0f, reduced.translationY)
        assertEquals(0f, reduced.rotationZ)
    }

    @Test
    fun verticalEscapeCancelsAndASidewaysSlideDoesNot() {
        assertTrue(bezelScrubShouldCancel(dxPx = 4f, dyPx = 25f, escapePx = 24f))
        assertFalse(bezelScrubShouldCancel(dxPx = 30f, dyPx = 10f, escapePx = 24f))
        assertFalse(bezelScrubShouldCancel(dxPx = 8f, dyPx = 24f, escapePx = 24f))
    }
}
