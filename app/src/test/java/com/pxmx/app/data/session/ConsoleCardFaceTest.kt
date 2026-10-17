package com.pxmx.app.data.session

import com.pxmx.app.data.FakeSharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsoleCardFaceTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: SessionStore

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        store = SessionStore(injectedPrefs = prefs)
    }

    @Test
    fun preferenceDefaultsFalse_andSetConsoleCardFaceSticks() {
        // 1. Defaults false when key is absent
        assertFalse("consoleCardFace must default to false", store.consoleCardFace.value)

        // 2. setConsoleCardFace(true) sticks in flow and prefs
        store.setConsoleCardFace(true)
        assertTrue("consoleCardFace must be true after setConsoleCardFace(true)", store.consoleCardFace.value)

        // 3. Verify persisted across new store instance
        val reloadedStore = SessionStore(injectedPrefs = prefs)
        assertTrue("consoleCardFace must stick on reloaded store", reloadedStore.consoleCardFace.value)

        // 4. Toggle back to false
        store.setConsoleCardFace(false)
        assertFalse("consoleCardFace must be false after toggle off", store.consoleCardFace.value)
        val freshStore = SessionStore(injectedPrefs = prefs)
        assertFalse("consoleCardFace must persist false", freshStore.consoleCardFace.value)
    }

    @Test
    fun purgeAll_resetsConsoleCardFace() {
        store.setConsoleCardFace(true)
        assertTrue(store.consoleCardFace.value)

        store.purgeAll()
        assertFalse("purgeAll must reset consoleCardFace to false", store.consoleCardFace.value)

        val reloaded = SessionStore(injectedPrefs = prefs)
        assertFalse("reloaded store after purgeAll must be false", reloaded.consoleCardFace.value)
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

        val reloaded1 = SessionStore(injectedPrefs = prefs)
        assertTrue(reloaded1.consoleCardFace.value)
        assertFalse(reloaded1.themeBackdrop.value)

        // setConsoleCardFace(true) then setThemeBackdrop(true) leaves backdrop true and console false.
        store.setThemeBackdrop(true)
        assertTrue(store.themeBackdrop.value)
        assertFalse(store.consoleCardFace.value)

        val reloaded2 = SessionStore(injectedPrefs = prefs)
        assertTrue(reloaded2.themeBackdrop.value)
        assertFalse(reloaded2.consoleCardFace.value)

        // setConsoleCardFace(false) does not turn the backdrop on.
        store.setThemeBackdrop(false)
        assertFalse(store.themeBackdrop.value)
        assertFalse(store.consoleCardFace.value)

        store.setConsoleCardFace(false)
        assertFalse("setting console false must not turn backdrop on", store.themeBackdrop.value)
        assertFalse(store.consoleCardFace.value)

        store.setConsoleCardFace(true)
        assertTrue(store.consoleCardFace.value)
        assertFalse(store.themeBackdrop.value)
        store.setConsoleCardFace(false)
        assertFalse(store.consoleCardFace.value)
        assertFalse("turning console off must leave the image background off", store.themeBackdrop.value)
    }
}
