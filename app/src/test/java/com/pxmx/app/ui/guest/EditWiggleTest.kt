package com.pxmx.app.ui.guest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditWiggleTest {

    @Test
    fun editWiggleDegrees_returnsExpectedSineValuesAndNodalPoints() {
        // At elapsed 0 and index 0 the result is 0
        assertEquals(0f, editWiggleDegrees(index = 0, elapsedMs = 0L, reduceMotion = false), 0.0001f)

        // At elapsed 70 and index 0 the result is 2 (quarter period = peak)
        assertEquals(2f, editWiggleDegrees(index = 0, elapsedMs = 70L, reduceMotion = false), 0.0001f)

        // Half period (140 ms) is 0
        assertEquals(0f, editWiggleDegrees(index = 0, elapsedMs = 140L, reduceMotion = false), 0.0001f)

        // Three-quarter period (210 ms) is -2 (negative peak)
        assertEquals(-2f, editWiggleDegrees(index = 0, elapsedMs = 210L, reduceMotion = false), 0.0001f)

        // Full period (280 ms) returns to 0
        assertEquals(0f, editWiggleDegrees(index = 0, elapsedMs = 280L, reduceMotion = false), 0.0001f)
    }

    @Test
    fun editWiggleDegrees_phaseShiftByIndex() {
        // Phase shifted by index * 70 ms
        // index 1 at elapsed 0 has phase shift 70 ms -> returns 2
        assertEquals(2f, editWiggleDegrees(index = 1, elapsedMs = 0L, reduceMotion = false), 0.0001f)

        // index 2 at elapsed 0 has phase shift 140 ms -> returns 0
        assertEquals(0f, editWiggleDegrees(index = 2, elapsedMs = 0L, reduceMotion = false), 0.0001f)

        // index 3 at elapsed 0 has phase shift 210 ms -> returns -2
        assertEquals(-2f, editWiggleDegrees(index = 3, elapsedMs = 0L, reduceMotion = false), 0.0001f)

        // Large elapsed time wraps cleanly around 280 ms period
        assertEquals(2f, editWiggleDegrees(index = 0, elapsedMs = 280000L + 70L, reduceMotion = false), 0.0001f)
    }

    @Test
    fun editWiggleDegrees_reduceMotionOrNegativeIndexReturnsZero() {
        // reduceMotion returns 0 regardless of phase or index
        assertEquals(0f, editWiggleDegrees(index = 0, elapsedMs = 70L, reduceMotion = true), 0.0f)
        assertEquals(0f, editWiggleDegrees(index = 1, elapsedMs = 0L, reduceMotion = true), 0.0f)

        // Negative index returns 0
        assertEquals(0f, editWiggleDegrees(index = -1, elapsedMs = 70L, reduceMotion = false), 0.0f)
        assertEquals(0f, editWiggleDegrees(index = -5, elapsedMs = 70L, reduceMotion = false), 0.0f)
    }

    @Test
    fun editDragShouldArm_checksMissAt239AndArmAt240WithinSlop() {
        val slopPx = 12f

        // 239 ms miss
        assertFalse(editDragShouldArm(heldMs = 239L, travelPx = 0f, slopPx = slopPx))
        assertFalse(editDragShouldArm(heldMs = 239L, travelPx = 10f, slopPx = slopPx))

        // 240 ms arm within slop
        assertTrue(editDragShouldArm(heldMs = 240L, travelPx = 0f, slopPx = slopPx))
        assertTrue(editDragShouldArm(heldMs = 240L, travelPx = 11.9f, slopPx = slopPx))
        assertTrue(editDragShouldArm(heldMs = 350L, travelPx = 5f, slopPx = slopPx))

        // Travel reaching or exceeding slop does not arm
        assertFalse(editDragShouldArm(heldMs = 240L, travelPx = 12f, slopPx = slopPx))
        assertFalse(editDragShouldArm(heldMs = 240L, travelPx = 15f, slopPx = slopPx))
    }

    @Test
    fun editDragShouldYieldToScroll_checksYieldBefore240WhenTravelExceedsSlop() {
        val slopPx = 12f

        // Yield when held < 240 and travel >= slop
        assertTrue(editDragShouldYieldToScroll(heldMs = 100L, travelPx = 12f, slopPx = slopPx))
        assertTrue(editDragShouldYieldToScroll(heldMs = 150L, travelPx = 25f, slopPx = slopPx))
        assertTrue(editDragShouldYieldToScroll(heldMs = 239L, travelPx = 12f, slopPx = slopPx))

        // Does not yield if travel is within slop
        assertFalse(editDragShouldYieldToScroll(heldMs = 100L, travelPx = 5f, slopPx = slopPx))
        assertFalse(editDragShouldYieldToScroll(heldMs = 239L, travelPx = 11.9f, slopPx = slopPx))

        // Does not yield once heldMs >= 240
        assertFalse(editDragShouldYieldToScroll(heldMs = 240L, travelPx = 15f, slopPx = slopPx))
        assertFalse(editDragShouldYieldToScroll(heldMs = 300L, travelPx = 20f, slopPx = slopPx))
    }

    @Test
    fun editDragStep_bothDirectionsAndDeadzone() {
        val thresholdPx = 48f

        // Upward step (translationYPx <= -thresholdPx)
        assertEquals(-1, editDragStep(translationYPx = -48f, thresholdPx = thresholdPx))
        assertEquals(-1, editDragStep(translationYPx = -50f, thresholdPx = thresholdPx))
        assertEquals(-1, editDragStep(translationYPx = -100f, thresholdPx = thresholdPx))

        // Downward step (translationYPx >= thresholdPx)
        assertEquals(1, editDragStep(translationYPx = 48f, thresholdPx = thresholdPx))
        assertEquals(1, editDragStep(translationYPx = 55f, thresholdPx = thresholdPx))
        assertEquals(1, editDragStep(translationYPx = 120f, thresholdPx = thresholdPx))

        // Within threshold deadzone (returns 0)
        assertEquals(0, editDragStep(translationYPx = 0f, thresholdPx = thresholdPx))
        assertEquals(0, editDragStep(translationYPx = 47.9f, thresholdPx = thresholdPx))
        assertEquals(0, editDragStep(translationYPx = -47.9f, thresholdPx = thresholdPx))
        assertEquals(0, editDragStep(translationYPx = 20f, thresholdPx = thresholdPx))
        assertEquals(0, editDragStep(translationYPx = -20f, thresholdPx = thresholdPx))
    }
}
