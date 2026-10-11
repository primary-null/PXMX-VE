package com.pxmx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPrefsTest {

    @Test
    fun defaults_matchSpecification() {
        val prefs = NotificationPrefs()
        assertTrue(prefs.masterEnabled)
        assertTrue(prefs.newGuest)
        assertTrue(prefs.backupFail)
        assertTrue(prefs.fencing)
        assertTrue(prefs.migrate)
        assertFalse(prefs.packageUpdates)
        assertFalse(prefs.externalStartStop)
        assertEquals(4, prefs.activeTriggersCount)
    }

    @Test
    fun activeTriggersCount_talliesCorrectly() {
        val allOn = NotificationPrefs(
            masterEnabled = true,
            newGuest = true,
            backupFail = true,
            fencing = true,
            migrate = true,
            packageUpdates = true,
            externalStartStop = true,
        )
        assertEquals(6, allOn.activeTriggersCount)

        val allOff = NotificationPrefs(
            masterEnabled = false,
            newGuest = false,
            backupFail = false,
            fencing = false,
            migrate = false,
            packageUpdates = false,
            externalStartStop = false,
        )
        assertEquals(0, allOff.activeTriggersCount)
    }
}
