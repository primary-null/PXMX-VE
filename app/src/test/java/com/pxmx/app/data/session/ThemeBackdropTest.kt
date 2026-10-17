package com.pxmx.app.data.session

import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.ui.theme.backdropSampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeBackdropTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: SessionStore

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        store = SessionStore(injectedPrefs = prefs)
    }

    @Test
    fun sampleSizeCalculations() {
        assertEquals(2, backdropSampleSize(4000, 3000, 2048))
        assertEquals(1, backdropSampleSize(1000, 800, 2048))
        assertEquals(1, backdropSampleSize(0, 100, 2048))
        assertEquals(2, backdropSampleSize(4096, 4096, 2048))
        assertEquals(4, backdropSampleSize(9000, 1000, 2048))
    }

    @Test
    fun themeBackdropPreferencePersistence() {
        // Defaults false when key is absent
        assertFalse(store.themeBackdrop.value)

        // setThemeBackdrop(true) sticks in flow and across new store
        store.setThemeBackdrop(true)
        assertTrue(store.themeBackdrop.value)
        val reloadedStore = SessionStore(injectedPrefs = prefs)
        assertTrue(reloadedStore.themeBackdrop.value)

        // setThemeBackdrop(false) sticks
        store.setThemeBackdrop(false)
        assertFalse(store.themeBackdrop.value)
        val freshStore = SessionStore(injectedPrefs = prefs)
        assertFalse(freshStore.themeBackdrop.value)

        // purgeAll resets themeBackdrop to false
        store.setThemeBackdrop(true)
        assertTrue(store.themeBackdrop.value)
        store.purgeAll()
        assertFalse(store.themeBackdrop.value)
        val purgedStore = SessionStore(injectedPrefs = prefs)
        assertFalse(purgedStore.themeBackdrop.value)
    }

    @Test
    fun defaultThemeOnFreshStoreIsOledDark() {
        val freshPrefs = FakeSharedPreferences()
        val freshStore = SessionStore(injectedPrefs = freshPrefs)
        assertEquals(ThemeMode.OLED_DARK, freshStore.themeMode.value)
    }

    @Test
    fun backdropSlidersDefaultsClampPersistenceAndPurgeAllReset() {
        // Defaults
        assertEquals(22, store.themeBackdropBlur.value)
        assertEquals(42, store.themeBackdropDim.value)
        assertEquals(55, store.themeBackdropVignette.value)

        // Persistence
        store.setThemeBackdropBlur(30)
        store.setThemeBackdropDim(50)
        store.setThemeBackdropVignette(65)
        assertEquals(30, store.themeBackdropBlur.value)
        assertEquals(50, store.themeBackdropDim.value)
        assertEquals(65, store.themeBackdropVignette.value)

        val reloaded = SessionStore(injectedPrefs = prefs)
        assertEquals(30, reloaded.themeBackdropBlur.value)
        assertEquals(50, reloaded.themeBackdropDim.value)
        assertEquals(65, reloaded.themeBackdropVignette.value)

        // Clamp blur 0..48
        store.setThemeBackdropBlur(-10)
        assertEquals(0, store.themeBackdropBlur.value)
        store.setThemeBackdropBlur(100)
        assertEquals(48, store.themeBackdropBlur.value)

        // Clamp dim 0..80
        store.setThemeBackdropDim(-5)
        assertEquals(0, store.themeBackdropDim.value)
        store.setThemeBackdropDim(99)
        assertEquals(80, store.themeBackdropDim.value)

        // Clamp vignette 0..80
        store.setThemeBackdropVignette(-1)
        assertEquals(0, store.themeBackdropVignette.value)
        store.setThemeBackdropVignette(150)
        assertEquals(80, store.themeBackdropVignette.value)

        // purgeAll reset
        store.purgeAll()
        assertEquals(22, store.themeBackdropBlur.value)
        assertEquals(42, store.themeBackdropDim.value)
        assertEquals(55, store.themeBackdropVignette.value)

        val purged = SessionStore(injectedPrefs = prefs)
        assertEquals(22, purged.themeBackdropBlur.value)
        assertEquals(42, purged.themeBackdropDim.value)
        assertEquals(55, purged.themeBackdropVignette.value)
    }

    @Test
    fun eitherOrMutualExclusion() {
        // setThemeBackdrop(true) then setConsoleCardFace(true) leaves console true and backdrop false.
        store.setThemeBackdrop(true)
        assertTrue(store.themeBackdrop.value)
        assertFalse(store.consoleCardFace.value)

        store.setConsoleCardFace(true)
        assertTrue(store.consoleCardFace.value)
        assertFalse(store.themeBackdrop.value)

        // setConsoleCardFace(true) then setThemeBackdrop(true) leaves backdrop true and console false.
        store.setThemeBackdrop(true)
        assertTrue(store.themeBackdrop.value)
        assertFalse(store.consoleCardFace.value)

        // setConsoleCardFace(false) does not turn the backdrop on.
        store.setThemeBackdrop(false)
        assertFalse(store.themeBackdrop.value)
        assertFalse(store.consoleCardFace.value)

        store.setConsoleCardFace(false)
        assertFalse(store.themeBackdrop.value)
        assertFalse(store.consoleCardFace.value)
    }
}
