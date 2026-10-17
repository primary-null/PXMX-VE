package com.pxmx.app.data.session

import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.ui.extras.StupidCardMotion
import com.pxmx.app.ui.extras.pickStupidCardMotion
import com.pxmx.app.ui.extras.stupidCartwheelDegrees
import com.pxmx.app.ui.extras.stupidCartwheelHopDp
import com.pxmx.app.ui.extras.stupidChipFlipDegrees
import com.pxmx.app.ui.extras.stupidCrtInitialScale
import com.pxmx.app.ui.extras.stupidCrtScaleXDuration
import com.pxmx.app.ui.extras.stupidCrtScaleYDuration
import com.pxmx.app.ui.extras.stupidFoldDegrees
import com.pxmx.app.ui.extras.stupidHeaderSquash
import com.pxmx.app.ui.extras.stupidLogShakeOffsets
import com.pxmx.app.ui.extras.stupidPopScales
import com.pxmx.app.ui.extras.stupidPressScale
import com.pxmx.app.ui.extras.stupidPressYaw
import com.pxmx.app.ui.extras.stupidSearchWobbleDegrees
import com.pxmx.app.ui.extras.stupidSpinTurns
import com.pxmx.app.ui.extras.stupidSwingDegrees
import com.pxmx.app.ui.extras.stupidTabYawDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StupidAnimationsTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: SessionStore

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        store = SessionStore(injectedPrefs = prefs)
    }

    @Test
    fun preferenceDefaultsFalse_andSetStupidAnimationsSticks() {
        // 1. Defaults false when key is absent
        assertFalse("stupidAnimations must default to false", store.stupidAnimations.value)

        // 2. setStupidAnimations(true) sticks in flow and prefs
        store.setStupidAnimations(true)
        assertTrue("stupidAnimations must be true after setStupidAnimations(true)", store.stupidAnimations.value)

        // 3. Verify persisted across new store instance
        val reloadedStore = SessionStore(injectedPrefs = prefs)
        assertTrue("stupidAnimations must stick on reloaded store", reloadedStore.stupidAnimations.value)

        // 4. Toggle back to false
        store.setStupidAnimations(false)
        assertFalse("stupidAnimations must be false after toggle off", store.stupidAnimations.value)
        val freshStore = SessionStore(injectedPrefs = prefs)
        assertFalse("stupidAnimations must persist false", freshStore.stupidAnimations.value)
    }

    @Test
    fun purgeAll_resetsStupidAnimations() {
        store.setStupidAnimations(true)
        assertTrue(store.stupidAnimations.value)

        store.purgeAll()
        assertFalse("purgeAll must reset stupidAnimations to false", store.stupidAnimations.value)

        val reloaded = SessionStore(injectedPrefs = prefs)
        assertFalse("reloaded store after purgeAll must be false", reloaded.stupidAnimations.value)
    }

    @Test
    fun pureFunction_stupidPressScale() {
        // Pressed and enabled -> 1.06f
        assertEquals(1.06f, stupidPressScale(pressed = true, enabled = true), 0.0001f)
        // Not pressed -> 1f
        assertEquals(1f, stupidPressScale(pressed = false, enabled = true), 0.0001f)
        // Disabled -> 1f
        assertEquals(1f, stupidPressScale(pressed = true, enabled = false), 0.0001f)
        assertEquals(1f, stupidPressScale(pressed = false, enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidPressYaw() {
        // Pressed and enabled -> 22f
        assertEquals(22f, stupidPressYaw(pressed = true, enabled = true), 0.0001f)
        // Not pressed -> 0f
        assertEquals(0f, stupidPressYaw(pressed = false, enabled = true), 0.0001f)
        // Disabled -> 0f
        assertEquals(0f, stupidPressYaw(pressed = true, enabled = false), 0.0001f)
        assertEquals(0f, stupidPressYaw(pressed = false, enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidSpinTurns() {
        // Enabled -> 3f
        assertEquals(3f, stupidSpinTurns(enabled = true), 0.0001f)
        // Disabled -> 1f
        assertEquals(1f, stupidSpinTurns(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidChipFlipDegrees() {
        assertEquals(90f, stupidChipFlipDegrees(enabled = true), 0.0001f)
        assertEquals(0f, stupidChipFlipDegrees(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidTabYawDegrees() {
        assertEquals(80f, stupidTabYawDegrees(enabled = true), 0.0001f)
        assertEquals(0f, stupidTabYawDegrees(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidHeaderSquash() {
        assertEquals(0.85f, stupidHeaderSquash(enabled = true), 0.0001f)
        assertEquals(1f, stupidHeaderSquash(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidLogShakeOffsets() {
        assertEquals(listOf(-8f, 8f, -4f, 0f), stupidLogShakeOffsets(enabled = true))
        assertEquals(listOf(0f), stupidLogShakeOffsets(enabled = false))
    }

    @Test
    fun pureFunction_stupidSearchWobbleDegrees() {
        assertEquals(listOf(-3f, 3f, 0f), stupidSearchWobbleDegrees(enabled = true))
        assertEquals(listOf(0f), stupidSearchWobbleDegrees(enabled = false))
    }

    @Test
    fun pureFunction_stupidCartwheelDegrees() {
        assertEquals(360f, stupidCartwheelDegrees(enabled = true), 0.0001f)
        assertEquals(0f, stupidCartwheelDegrees(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidCartwheelHopDp() {
        assertEquals(-8f, stupidCartwheelHopDp(enabled = true), 0.0001f)
        assertEquals(0f, stupidCartwheelHopDp(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidFoldDegrees() {
        assertEquals(-90f, stupidFoldDegrees(enabled = true), 0.0001f)
        assertEquals(0f, stupidFoldDegrees(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidCrtInitialScale() {
        assertEquals(0.04f, stupidCrtInitialScale(enabled = true), 0.0001f)
        assertEquals(1f, stupidCrtInitialScale(enabled = false), 0.0001f)
    }

    @Test
    fun pureFunction_stupidCrtDurations() {
        assertEquals(140, stupidCrtScaleXDuration(enabled = true))
        assertEquals(0, stupidCrtScaleXDuration(enabled = false))
        assertEquals(420, stupidCrtScaleYDuration(enabled = true))
        assertEquals(0, stupidCrtScaleYDuration(enabled = false))
    }

    @Test
    fun pureFunction_stupidSwingDegrees() {
        assertEquals(listOf(10f, -8f, 5f, 0f), stupidSwingDegrees(enabled = true))
        assertEquals(listOf(0f), stupidSwingDegrees(enabled = false))
    }

    @Test
    fun pureFunction_stupidPopScales() {
        assertEquals(listOf(0.86f, 1.08f, 1f), stupidPopScales(enabled = true))
        assertEquals(listOf(1f), stupidPopScales(enabled = false))
    }

    @Test
    fun pureFunction_pickStupidCardMotion_allMotionsReachable() {
        val pickedMotions = (0..5).map { press ->
            pickStupidCardMotion(cardIdentity = "vm-100", pressCount = press)
        }.toSet()
        assertEquals(StupidCardMotion.values().toSet(), pickedMotions)
    }
}
