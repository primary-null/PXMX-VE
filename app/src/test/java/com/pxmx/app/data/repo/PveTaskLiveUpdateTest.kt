package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PveTaskLiveUpdateTest {

    private class RecordingNotificationPoster : TaskNotificationPoster {
        data class Call(val action: String, val id: Int, val title: String? = null, val text: String? = null)
        val calls = mutableListOf<Call>()

        override fun postOngoing(id: Int, title: String, text: String) {
            calls.add(Call("postOngoing", id, title, text))
        }

        override fun postCompleted(id: Int, title: String, text: String) {
            calls.add(Call("postCompleted", id, title, text))
        }

        override fun cancel(id: Int) {
            calls.add(Call("cancel", id))
        }
    }

    @Test
    fun testShouldPostConditions() {
        // SDK < 26 is false
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 25, hasPermission = true, host = "pve.example.com"))

        // Missing permission is false
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 36, hasPermission = false, host = "pve.example.com"))
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 37, hasPermission = false, host = "pve.example.com"))

        // Demo host is false
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 36, hasPermission = true, host = "demo"))
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 36, hasPermission = true, host = "demo.local"))
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 36, hasPermission = true, host = "DEMO"))
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 36, hasPermission = true, host = ""))
        assertFalse(PveTaskLiveUpdate.shouldPost(sdkInt = 36, hasPermission = true, host = null))

        // SDK >= 26 (including 27, 34, 35, 36, 37), permission granted, non-demo host is true
        assertTrue(PveTaskLiveUpdate.shouldPost(sdkInt = 27, hasPermission = true, host = "pve.example.com"))
        assertTrue(PveTaskLiveUpdate.shouldPost(sdkInt = 34, hasPermission = true, host = "pve.example.com"))
        assertTrue(PveTaskLiveUpdate.shouldPost(sdkInt = 35, hasPermission = true, host = "pve.example.com"))
        assertTrue(PveTaskLiveUpdate.shouldPost(sdkInt = 36, hasPermission = true, host = "pve.example.com"))
        assertTrue(PveTaskLiveUpdate.shouldPost(sdkInt = 37, hasPermission = true, host = "192.168.1.100"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWatchTaskSuccessfulCompletion() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "pve.example.com", username = "root")
        sessionStore.setSession(SessionState(config, ticket = "fake-ticket", username = "root@pam"))

        val poster = RecordingNotificationPoster()
        var polls = 0
        val liveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ ->
                polls++
                if (polls == 1) {
                    Result.success(TaskStatus(status = "running"))
                } else {
                    Result.success(TaskStatus(status = "stopped", exitstatus = "OK"))
                }
            },
            scope = this,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 36,
            completionDismissDelayMs = 100L,
        )

        val upid = "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"
        val notifId = upid.hashCode()

        val job = liveUpdate.watchTask(upid)
        assertNotNull(job)

        // Initial notification posted immediately
        assertEquals(1, poster.calls.size)
        assertEquals("postOngoing", poster.calls[0].action)
        assertEquals(notifId, poster.calls[0].id)
        assertEquals("BACKUP 100", poster.calls[0].title)

        // Advance past interval 2s (first poll: running)
        advanceTimeBy(2050L)
        runCurrent()
        assertEquals(1, poster.calls.size)

        // Advance past next interval 2s (second poll: stopped+OK) + completion delay
        advanceTimeBy(2150L)
        runCurrent()

        // Should have postCompleted and cancel
        assertTrue(poster.calls.any { it.action == "postCompleted" && it.text == "Completed" && it.title == "BACKUP 100" })
        assertTrue(poster.calls.any { it.action == "cancel" && it.id == notifId })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWatchTaskFailureShowsExitStatus() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "pve.example.com", username = "root")
        sessionStore.setSession(SessionState(config, ticket = "fake-ticket", username = "root@pam"))

        val poster = RecordingNotificationPoster()
        val liveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ ->
                Result.success(TaskStatus(status = "stopped", exitstatus = "command failed (exit code 2)"))
            },
            scope = this,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 36,
            completionDismissDelayMs = 100L,
        )

        val upid = "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"
        val notifId = upid.hashCode()

        liveUpdate.watchTask(upid)

        // Advance past interval and completion delay
        advanceTimeBy(2200L)
        runCurrent()

        assertTrue(poster.calls.any { it.action == "postCompleted" && it.text == "command failed (exit code 2)" })
        assertTrue(poster.calls.any { it.action == "cancel" && it.id == notifId })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWatchTaskCancelsOnSessionChange() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "pve.example.com", username = "root")
        sessionStore.setSession(SessionState(config, ticket = "fake-ticket", username = "root@pam"))

        val poster = RecordingNotificationPoster()
        val liveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ ->
                Result.success(TaskStatus(status = "running"))
            },
            scope = this,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 36,
        )

        val upid = "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"
        val notifId = upid.hashCode()

        liveUpdate.watchTask(upid)
        assertEquals(1, poster.calls.size)
        assertEquals("postOngoing", poster.calls[0].action)

        // Session changes (e.g. logout or relogin)
        sessionStore.clearSession()

        // Advance past interval 2s
        advanceTimeBy(2100L)
        runCurrent()

        // Notification cancelled
        assertTrue(poster.calls.any { it.action == "cancel" && it.id == notifId })
    }

    @Test
    fun testNoOpWhenConditionsNotMet() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val poster = RecordingNotificationPoster()

        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        // Demo host -> no-op
        sessionStore.setSession(SessionState(ServerConfig(host = "demo", username = "root"), ticket = "demo", username = "root@pam"))
        val demoLiveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ -> Result.success(TaskStatus(status = "running")) },
            scope = testScope,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 36,
        )
        assertNull(demoLiveUpdate.watchTask("UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"))
        assertEquals(0, poster.calls.size)

        // SDK < 26 -> no-op
        sessionStore.setSession(SessionState(ServerConfig(host = "pve.example.com", username = "root"), ticket = "ticket", username = "root@pam"))
        val oldSdkLiveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ -> Result.success(TaskStatus(status = "running")) },
            scope = testScope,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 25,
        )
        assertNull(oldSdkLiveUpdate.watchTask("UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"))
        assertEquals(0, poster.calls.size)

        // Permission denied -> no-op
        val noPermLiveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ -> Result.success(TaskStatus(status = "running")) },
            scope = testScope,
            notificationPoster = poster,
            permissionChecker = { false },
            sdkInt = 36,
        )
        assertNull(noPermLiveUpdate.watchTask("UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"))
        assertEquals(0, poster.calls.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCompletionCardPersistsByDefault() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "pve.example.com", username = "root")
        sessionStore.setSession(SessionState(config, ticket = "fake-ticket", username = "root@pam"))

        val poster = RecordingNotificationPoster()
        val liveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ ->
                Result.success(TaskStatus(status = "stopped", exitstatus = "OK"))
            },
            scope = this,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 35, // Below 36 works too
            completionDismissDelayMs = null, // Default
        )

        val upid = "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"
        val notifId = upid.hashCode()

        liveUpdate.watchTask(upid)
        advanceTimeBy(2500L)
        runCurrent()

        // postCompleted called, but cancel is NOT called (persists)
        assertTrue(poster.calls.any { it.action == "postCompleted" && it.id == notifId })
        assertFalse(poster.calls.any { it.action == "cancel" && it.id == notifId })
    }

    @Test
    fun testDedupAndCapInFlightWatches() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "pve.example.com", username = "root")
        sessionStore.setSession(SessionState(config, ticket = "fake-ticket", username = "root@pam"))

        val poster = RecordingNotificationPoster()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val liveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ -> Result.success(TaskStatus(status = "running")) },
            scope = testScope,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 34,
        )

        val upid1 = "UPID:alpha:00000001:00000000:66D1B000:vzdump:101:root@pam:"
        val job1 = liveUpdate.watchTask(upid1)
        assertNotNull(job1)
        val job1Repeat = liveUpdate.watchTask(upid1)
        assertSame(job1, job1Repeat) // Dedup returns same job

        // Cap at 5
        for (i in 2..5) {
            val u = "UPID:alpha:0000000$i:00000000:66D1B000:vzdump:10$i:root@pam:"
            assertNotNull(liveUpdate.watchTask(u))
        }
        val upid6 = "UPID:alpha:00000006:00000000:66D1B000:vzdump:106:root@pam:"
        assertNull(liveUpdate.watchTask(upid6)) // Capped at MAX_IN_FLIGHT_WATCHES
    }

    @Test
    fun humanizeExitStatus_ok_returnsOk() {
        assertEquals("OK", PveTaskLiveUpdate.humanizeExitStatus("OK"))
    }

    @Test
    fun humanizeExitStatus_nullOrBlank_returnsFailed() {
        assertEquals("Failed", PveTaskLiveUpdate.humanizeExitStatus(null))
        assertEquals("Failed", PveTaskLiveUpdate.humanizeExitStatus(""))
        assertEquals("Failed", PveTaskLiveUpdate.humanizeExitStatus("   "))
    }

    @Test
    fun humanizeExitStatus_wrongContentType_mapsToDatastoreCopy() {
        val raw = "could not get storage information for 'vm-disks': can't use storage 'vm-disks' for backups - wrong content type"
        val expected = "That datastore does not hold backups. Pick one whose content includes backup."
        assertEquals(expected, PveTaskLiveUpdate.humanizeExitStatus(raw))
        assertEquals(expected, PveTaskLiveUpdate.humanizeExitStatus("can't use storage 'vm-disks' for backups - wrong content type"))
        assertEquals(expected, PveTaskLiveUpdate.humanizeExitStatus("wrong content type"))
        assertEquals(expected, PveTaskLiveUpdate.humanizeExitStatus("can't use storage 'local-lvm' for backups"))
    }

    @Test
    fun humanizeExitStatus_unmatchedError_preservesError() {
        assertEquals("command failed (exit code 2)", PveTaskLiveUpdate.humanizeExitStatus("command failed (exit code 2)"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testWatchTaskWrongContentTypeShowsHumanizedStatus() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        val config = ServerConfig(host = "pve.example.com", username = "root")
        sessionStore.setSession(SessionState(config, ticket = "fake-ticket", username = "root@pam"))

        val poster = RecordingNotificationPoster()
        val rawErr = "could not get storage information for 'vm-disks': can't use storage 'vm-disks' for backups - wrong content type"
        val liveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ ->
                Result.success(TaskStatus(status = "stopped", exitstatus = rawErr))
            },
            scope = this,
            notificationPoster = poster,
            permissionChecker = { true },
            sdkInt = 36,
            completionDismissDelayMs = 100L,
        )

        val upid = "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"

        liveUpdate.watchTask(upid)
        advanceTimeBy(2200L)
        runCurrent()

        val expected = "That datastore does not hold backups. Pick one whose content includes backup."
        assertTrue(poster.calls.any { it.action == "postCompleted" && it.text == expected && it.title == "BACKUP 100" })
    }
}
