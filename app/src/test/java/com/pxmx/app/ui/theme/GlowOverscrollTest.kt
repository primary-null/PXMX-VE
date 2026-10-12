package com.pxmx.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("MagicNumber")
class GlowOverscrollTest {

    @Test
    fun geometry_w1000_h2000() {
        val w = 1000f
        val h = 2000f
        assertEquals(1200f, GlowOverscrollMath.r(w), 1e-3f)
        assertEquals(160.77f, GlowOverscrollMath.capH(w), 0.01f)
        assertEquals(160.77f, GlowOverscrollMath.boundsH(w, h), 0.01f)
        assertEquals(1f, GlowOverscrollMath.baseGlowScale(w, h), 1e-3f)
        assertEquals(-1039.23f, GlowOverscrollMath.centerY(w, h), 0.01f)
    }

    @Test
    fun geometry_w1000_h100() {
        val w = 1000f
        val h = 100f
        assertEquals(100f, GlowOverscrollMath.boundsH(w, h), 1e-3f)
        assertEquals(0.1f, GlowOverscrollMath.baseGlowScale(w, h), 1e-3f)
        val centerY = GlowOverscrollMath.centerY(w, h)
        assertTrue("centerY must be negative", centerY < 0f)
        assertEquals(-1100f, centerY, 1e-3f)
    }

    @Test
    fun decelerate_interpolator() {
        assertEquals(0f, GlowOverscrollMath.decelerate(0f), 1e-3f)
        assertEquals(0.75f, GlowOverscrollMath.decelerate(0.5f), 1e-3f)
        assertEquals(1f, GlowOverscrollMath.decelerate(1f), 1e-3f)
    }

    @Test
    fun pullScale_boundsH160_77() {
        val boundsH = 160.77f
        assertEquals(0f, GlowOverscrollMath.pullScale(0.01f, boundsH), 1e-3f)
        assertEquals(0.4961f, GlowOverscrollMath.pullScale(0.05f, boundsH), 1e-3f)
        assertEquals(0.8873f, GlowOverscrollMath.pullScale(1.0f, boundsH), 1e-3f)

        // Monotonic
        val s1 = GlowOverscrollMath.pullScale(0.02f, boundsH)
        val s2 = GlowOverscrollMath.pullScale(0.05f, boundsH)
        val s3 = GlowOverscrollMath.pullScale(0.1f, boundsH)
        val s4 = GlowOverscrollMath.pullScale(0.5f, boundsH)
        val s5 = GlowOverscrollMath.pullScale(1.0f, boundsH)
        assertTrue(s1 <= s2)
        assertTrue(s2 <= s3)
        assertTrue(s3 <= s4)
        assertTrue(s4 <= s5)

        // Never exceeds 1
        val sHuge = GlowOverscrollMath.pullScale(100f, boundsH)
        assertTrue(sHuge <= 1f)
    }

    @Test
    fun geometryWidth_capsLargeContainers() {
        val containerW = 1380f
        val maxGeoPx = 440f // 160.dp at 2.75x
        val geoW = GlowOverscrollMath.geometryWidth(containerW, maxGeoPx)
        assertEquals(440f, geoW, 1e-3f)

        // Ensure smaller container is uncapped
        assertEquals(400f, GlowOverscrollMath.geometryWidth(400f, maxGeoPx), 1e-3f)

        // Geometry uses capped geoW, not full containerW
        val r = GlowOverscrollMath.r(geoW)
        assertEquals(528f, r, 1e-3f)
        val capH = GlowOverscrollMath.capH(geoW)
        assertEquals(70.74f, capH, 0.02f)
    }

    @Test
    fun pullAlpha_saturationAndAccumulation() {
        assertEquals(0.08f, GlowOverscrollMath.pullAlpha(0f, 0.1f), 1e-3f)
        assertEquals(0.25f, GlowOverscrollMath.pullAlpha(0f, 0.5f), 1e-3f)

        // Repeated pulls saturate and never decrease
        var alpha = 0f
        alpha = GlowOverscrollMath.pullAlpha(alpha, 0.05f)
        val first = alpha
        alpha = GlowOverscrollMath.pullAlpha(alpha, 0.05f)
        val second = alpha
        assertTrue(second >= first)
        alpha = GlowOverscrollMath.pullAlpha(alpha, 0.5f)
        assertEquals(0.25f, alpha, 1e-3f)
        alpha = GlowOverscrollMath.pullAlpha(alpha, 0.1f)
        assertEquals(0.25f, alpha, 1e-3f)
    }

    @Test
    fun absorb_scalingAndAlpha() {
        // v=50 clamps to 100, scale finish ≈ 0.0325
        assertEquals(0.0325f, GlowOverscrollMath.absorbScaleFinish(50f), 1e-3f)

        // v=1000: duration 20.15, scale 0.775, alpha 0.15 (GLOW_ALPHA_START floor)
        assertEquals(20.15f, GlowOverscrollMath.absorbDuration(1000f), 1e-3f)
        assertEquals(0.775f, GlowOverscrollMath.absorbScaleFinish(1000f), 1e-3f)
        assertEquals(0.15f, GlowOverscrollMath.absorbAlphaFinish(1000f), 1e-3f)

        // v=2000: alpha 0.15 (floored by GLOW_ALPHA_START)
        assertEquals(0.15f, GlowOverscrollMath.absorbAlphaFinish(2000f), 1e-3f)

        // v=2500: alpha 0.15
        assertEquals(0.15f, GlowOverscrollMath.absorbAlphaFinish(2500f), 1e-3f)

        // v=5000: scale 1.0, alpha 0.25 (MAX_ALPHA cap)
        assertEquals(1.0f, GlowOverscrollMath.absorbScaleFinish(5000f), 1e-3f)
        assertEquals(0.25f, GlowOverscrollMath.absorbAlphaFinish(5000f), 1e-3f)

        // v=20000 clamps to 10000: duration 200.15
        assertEquals(200.15f, GlowOverscrollMath.absorbDuration(20000f), 1e-3f)
    }

    @Test
    fun translateX_w1000() {
        val w = 1000f
        assertEquals(0f, GlowOverscrollMath.translateX(w, 0.5f), 1e-3f)
        assertEquals(-250f, GlowOverscrollMath.translateX(w, 0f), 1e-3f)
        assertEquals(250f, GlowOverscrollMath.translateX(w, 1f), 1e-3f)

        // Out of [0, 1] clamp
        assertEquals(-250f, GlowOverscrollMath.translateX(w, -0.5f), 1e-3f)
        assertEquals(250f, GlowOverscrollMath.translateX(w, 1.5f), 1e-3f)
    }

    @Test
    fun stateMachine_pullAndHoldLifecycle() {
        val physics = GlowEdgePhysics()
        physics.setSize(1000f, 2000f)

        // IDLE -> onPull -> PULL
        assertEquals(GlowEdgeState.IDLE, physics.state)
        assertTrue(physics.isFinished)
        physics.onPull(0.1f, 0.5f, 1000L)
        assertEquals(GlowEdgeState.PULL, physics.state)
        assertFalse(physics.isFinished)
        assertEquals(1000L, physics.startTime)

        // Second onPull restarts hold
        physics.onPull(0.05f, 0.5f, 1100L)
        assertEquals(GlowEdgeState.PULL, physics.state)
        assertEquals(1100L, physics.startTime)

        // 167 ms with no pull -> PULL_DECAY
        physics.update(1100L + 167L)
        assertEquals(GlowEdgeState.PULL_DECAY, physics.state)

        // onPull during PULL_DECAY before 2000 ms is ignored (alpha and scale unchanged)
        val alphaDuringDecay = physics.glowAlpha
        val scaleDuringDecay = physics.glowScaleY
        physics.onPull(0.1f, 0.5f, 1500L)
        assertEquals(GlowEdgeState.PULL_DECAY, physics.state)
        assertEquals(alphaDuringDecay, physics.glowAlpha, 1e-4f)
        assertEquals(scaleDuringDecay, physics.glowScaleY, 1e-4f)
    }

    @Test
    fun stateMachine_releaseAndDecay() {
        val pullPhysics = GlowEdgePhysics()
        pullPhysics.setSize(1000f, 2000f)
        pullPhysics.onPull(0.2f, 0.5f, 2000L)
        val pullAlpha = pullPhysics.glowAlpha
        pullPhysics.onRelease(2100L)
        assertEquals(GlowEdgeState.RECEDE, pullPhysics.state)

        // At +300 ms alpha ≈ start * 0.25 (interp 0.75)
        pullPhysics.update(2100L + 300L)
        assertEquals(pullAlpha * 0.25f, pullPhysics.glowAlpha, 1e-3f)

        // At +600 ms alpha=0 and scale=0, then IDLE
        pullPhysics.update(2100L + 600L)
        assertEquals(0f, pullPhysics.glowAlpha, 1e-4f)
        assertEquals(0f, pullPhysics.glowScaleY, 1e-4f)
        assertEquals(GlowEdgeState.IDLE, pullPhysics.state)
        assertTrue(pullPhysics.isFinished)

        // onRelease from IDLE is a no-op
        val idlePhysics = GlowEdgePhysics()
        idlePhysics.onRelease(3000L)
        assertEquals(GlowEdgeState.IDLE, idlePhysics.state)
    }

    @Test
    fun stateMachine_absorbAndDisplacement() {
        val absorbPhysics = GlowEdgePhysics()
        absorbPhysics.onAbsorb(1000f, 3000L)
        assertEquals(GlowEdgeState.ABSORB, absorbPhysics.state)
        assertEquals(20.15f, absorbPhysics.duration, 1e-3f)
        assertEquals(0.775f, absorbPhysics.scaleFinish, 1e-3f)
        assertEquals(0.15f, absorbPhysics.alphaFinish, 1e-3f)

        // onRelease from ABSORB is a no-op
        absorbPhysics.onRelease(3010L)
        assertEquals(GlowEdgeState.ABSORB, absorbPhysics.state)

        // ABSORB runs to its duration, then RECEDE (600 ms), then IDLE
        absorbPhysics.update(3000L + 21L) // duration for v=1000 is 20.15ms
        assertEquals(GlowEdgeState.RECEDE, absorbPhysics.state)
        absorbPhysics.update(3000L + 21L + 600L)
        assertEquals(GlowEdgeState.IDLE, absorbPhysics.state)

        // absorbOrRelease while in PULL results in RECEDE, not ABSORB
        val pullForAbsorb = GlowEdgePhysics()
        pullForAbsorb.onPull(0.1f, 0.5f, 4000L)
        assertEquals(GlowEdgeState.PULL, pullForAbsorb.state)
        pullForAbsorb.absorbOrRelease(1000f, 4050L)
        assertEquals(GlowEdgeState.RECEDE, pullForAbsorb.state)

        // Displacement lerp: (d + target)/2 per update; absorb resets target to 0.5
        val dispPhysics = GlowEdgePhysics()
        dispPhysics.onPull(0.1f, 0.0f, 5000L)
        assertEquals(0.5f, dispPhysics.displacement, 1e-4f)
        assertEquals(0.0f, dispPhysics.targetDisplacement, 1e-4f)
        dispPhysics.update(5010L)
        assertEquals(0.25f, dispPhysics.displacement, 1e-4f)
        dispPhysics.update(5020L)
        assertEquals(0.125f, dispPhysics.displacement, 1e-4f)
        dispPhysics.onAbsorb(1000f, 6000L)
        assertEquals(0.5f, dispPhysics.targetDisplacement, 1e-4f)

        // Never IDLE while alpha or scale is above epsilon
        val checkPhysics = GlowEdgePhysics()
        checkPhysics.setSize(1000f, 2000f)
        checkPhysics.onPull(0.2f, 0.5f, 7000L)
        checkPhysics.onRelease(7100L)
        for (t in 0..590 step 50) {
            checkPhysics.update(7100L + t)
            if (checkPhysics.glowAlpha > GlowOverscrollMath.EPSILON ||
                checkPhysics.glowScaleY > GlowOverscrollMath.EPSILON
            ) {
                assertFalse("Must not be IDLE while active", checkPhysics.state == GlowEdgeState.IDLE)
            }
        }
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
