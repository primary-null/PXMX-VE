package com.pxmx.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowOverscrollTest {

    @Test
    fun clamp_clampsToZeroAndOne() {
        assertEquals(0f, GlowOverscrollMath.clamp(-0.5f), 0.0001f)
        assertEquals(0f, GlowOverscrollMath.clamp(0f), 0.0001f)
        assertEquals(0.42f, GlowOverscrollMath.clamp(0.42f), 0.0001f)
        assertEquals(1f, GlowOverscrollMath.clamp(1f), 0.0001f)
        assertEquals(1f, GlowOverscrollMath.clamp(2.5f), 0.0001f)
    }

    @Test
    fun intensityFromDelta_computesProportionAndClamps() {
        val maxDist = 300f

        // Zero delta
        assertEquals(0f, GlowOverscrollMath.intensityFromDelta(0f, maxDist), 0.0001f)

        // Positive delta (top pull)
        assertEquals(0.5f, GlowOverscrollMath.intensityFromDelta(150f, maxDist), 0.0001f)

        // Negative delta (bottom pull)
        assertEquals(0.5f, GlowOverscrollMath.intensityFromDelta(-150f, maxDist), 0.0001f)

        // Clamped at 1.0f
        assertEquals(1.0f, GlowOverscrollMath.intensityFromDelta(450f, maxDist), 0.0001f)
        assertEquals(1.0f, GlowOverscrollMath.intensityFromDelta(-600f, maxDist), 0.0001f)

        // Degenerate max distance handles safely
        assertEquals(0f, GlowOverscrollMath.intensityFromDelta(100f, 0f), 0.0001f)
        assertEquals(0f, GlowOverscrollMath.intensityFromDelta(100f, -50f), 0.0001f)
    }

    @Test
    fun accumulate_addsDeltaAndClampsAtOne() {
        val maxDist = 300f

        // Start from 0
        val step1 = GlowOverscrollMath.accumulate(0f, 90f, maxDist)
        assertEquals(0.3f, step1, 0.0001f)

        // Add second pull
        val step2 = GlowOverscrollMath.accumulate(step1, 90f, maxDist)
        assertEquals(0.6f, step2, 0.0001f)

        // Overflow clamps at 1.0
        val step3 = GlowOverscrollMath.accumulate(step2, 200f, maxDist)
        assertEquals(1.0f, step3, 0.0001f)

        // Negative delta handled via absolute value
        val stepNeg = GlowOverscrollMath.accumulate(0.1f, -60f, maxDist)
        assertEquals(0.3f, stepNeg, 0.0001f)

        // Degenerate max distance
        assertEquals(0.5f, GlowOverscrollMath.accumulate(0.5f, 50f, 0f), 0.0001f)
    }

    @Test
    fun decayStep_decreasesIntensityDownToZero() {
        val start = 0.5f
        val after1 = GlowOverscrollMath.decayStep(start, decayRate = 0.1f)
        assertEquals(0.4f, after1, 0.0001f)

        // Small remaining intensity decays to zero, never goes negative
        val small = 0.03f
        val afterSmall = GlowOverscrollMath.decayStep(small, decayRate = 0.05f)
        assertEquals(0f, afterSmall, 0.0001f)

        // Default decay rate
        val defaultStep = GlowOverscrollMath.decayStep(1.0f)
        assertEquals(0.95f, defaultStep, 0.0001f)
    }

    @Test
    fun isActive_detectsAboveThreshold() {
        assertFalse(GlowOverscrollMath.isActive(0f))
        assertFalse(GlowOverscrollMath.isActive(GlowOverscrollMath.ACTIVE_EPSILON / 2f))
        assertTrue(GlowOverscrollMath.isActive(0.002f))
        assertTrue(GlowOverscrollMath.isActive(0.5f))
        assertTrue(GlowOverscrollMath.isActive(1.0f))
    }

    @Test
    fun glowOverscrollFactory_equalsAndHashCode_contract() {
        val orange1 = GlowOverscrollFactory(Color(0xFFFF9800))
        val orange2 = GlowOverscrollFactory(Color(0xFFFF9800))
        val blue = GlowOverscrollFactory(Color(0xFF42A5F5))

        assertEquals(orange1, orange2)
        assertEquals(orange1.hashCode(), orange2.hashCode())
        assertNotEquals(orange1, blue)
        assertNotEquals(orange1, "other")
    }
}
