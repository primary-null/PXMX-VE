package com.pxmx.app.data

import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.model.SessionResumeInfo
import com.pxmx.app.data.session.SessionStore
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Migration guard: cards written by 0.6.9 that pointed the jump-back banner at
 * "demo" but carried a real profileId must be dropped on load, never resumed.
 */
class SessionResumeMigrationTest {

    private fun writeCard(prefs: FakeSharedPreferences, card: SessionResumeInfo) {
        prefs.edit { putString("previous_session_json", AppJson.encodeToString(card)) }
    }

    @Test
    fun demoCardFromOlderVersionIsDroppedAndPrefsCleared() {
        val fakePrefs = FakeSharedPreferences()
        val card = SessionResumeInfo(
            profileId = "profile-real-server",
            hostDisplay = "demo:8006",
            userDisplay = "root",
            version = "8.3.0-8.3",
            lastLoginEpochMs = 1L,
            wasActiveSession = true,
        )
        writeCard(fakePrefs, card)

        val store = SessionStore(injectedPrefs = fakePrefs)

        assertNull(store.previousSession.value)
        assertNull(fakePrefs.getString("previous_session_json", null))
    }

    @Test
    fun realCardStillLoads() {
        val fakePrefs = FakeSharedPreferences()
        val card = SessionResumeInfo(
            profileId = "profile-real-server",
            hostDisplay = "192.0.2.10:8006",
            userDisplay = "root",
            version = "8.3.0-8.3",
            lastLoginEpochMs = 1L,
            wasActiveSession = true,
        )
        writeCard(fakePrefs, card)

        val store = SessionStore(injectedPrefs = fakePrefs)

        assertNotNull(store.previousSession.value)
        assertEquals("192.0.2.10:8006", store.previousSession.value?.hostDisplay)
        assertEquals("profile-real-server", store.previousSession.value?.profileId)
    }

    @Test
    fun demoPrefixedButDifferentHostNameStillLoads() {
        // "demo.lab.example" must NOT be treated as the demo host (exact match only).
        val fakePrefs = FakeSharedPreferences()
        val card = SessionResumeInfo(
            profileId = "profile-real-server",
            hostDisplay = "demo.lab.example:8006",
            userDisplay = "root",
            version = "8.3.0-8.3",
            lastLoginEpochMs = 1L,
            wasActiveSession = true,
        )
        writeCard(fakePrefs, card)

        val store = SessionStore(injectedPrefs = fakePrefs)

        assertNotNull(store.previousSession.value)
        assertEquals("demo.lab.example:8006", store.previousSession.value?.hostDisplay)
    }
}
