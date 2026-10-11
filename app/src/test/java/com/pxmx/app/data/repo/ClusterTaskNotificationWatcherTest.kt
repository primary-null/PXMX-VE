package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.model.NotificationPrefs
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClusterTaskNotificationWatcherTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var recentActionRegistry: RecentActionRegistry
    private lateinit var recordingPoster: RecordingTaskNotificationPoster
    private lateinit var liveUpdate: PveTaskLiveUpdate
    private val posted = mutableListOf<Triple<Int, String, String>>()

    private class RecordingTaskNotificationPoster : TaskNotificationPoster {
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

    @Before
    fun setup() {
        val fakePrefs = FakeSharedPreferences()
        sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "192.168.1.100", port = 8006),
                ticket = "ticket",
                csrf = "csrf",
            )
        )
        recentActionRegistry = RecentActionRegistry()
        recordingPoster = RecordingTaskNotificationPoster()
        liveUpdate = PveTaskLiveUpdate(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            taskStatusProvider = { _, _ -> Result.success(TaskStatus(status = "stopped", exitstatus = "OK")) },
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
            notificationPoster = recordingPoster,
            permissionChecker = { true },
            sdkInt = 36,
        )
        posted.clear()
    }

    private fun createWatcher(
        configFetcher: suspend (String, String, Long) -> Map<String, Any>? = { _, _, vmid ->
            mapOf("name" to "nova", "ide2" to "local:iso/ubuntu.iso,media=cdrom")
        }
    ): ClusterTaskNotificationWatcher {
        return ClusterTaskNotificationWatcher(
            sessionStore = sessionStore,
            recentActionRegistry = recentActionRegistry,
            liveUpdate = liveUpdate,
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
            configFetcher = configFetcher,
            notificationPoster = { id, title, text ->
                posted.add(Triple(id, title, text))
            },
        )
    }

    @Test
    fun firstPoll_seedsUpids_doesNotPost() = runTest {
        val watcher = createWatcher()
        val initialTasks = listOf(
            mapOf(
                "upid" to "UPID:pve:0001:0001:66D00001:qmcreate:100:root@pam:",
                "type" to "qmcreate",
                "id" to "100",
                "node" to "pve",
            ),
            mapOf(
                "upid" to "UPID:pve:0002:0001:66D00002:vzdump:101:root@pam:",
                "type" to "vzdump",
                "id" to "101",
                "status" to "error: failed",
                "node" to "pve",
            ),
        )

        watcher.onClusterTasks(initialTasks)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun secondPoll_newGuestQmCreate_postsEnrichedNotification() = runTest {
        val watcher = createWatcher { _, _, vmid ->
            mapOf("name" to "nova", "ide2" to "local:iso/ubuntu.iso,media=cdrom")
        }

        // Seed
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0001:0001:66D00001:qmcreate:100:root@pam:", "type" to "qmcreate", "id" to "100")
        ))
        assertTrue(posted.isEmpty())

        // New task
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0002:0001:66D00002:qmcreate:101:root@pam:", "type" to "qmcreate", "id" to "101", "node" to "pve")
        ))

        assertEquals(1, posted.size)
        val (_, title, text) = posted[0]
        assertEquals("New VM 101", title)
        assertEquals("nova · using local:iso/ubuntu.iso", text)
    }

    @Test
    fun demoHost_neverPosts() = runTest {
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "demo", port = 8006),
                ticket = "ticket",
                csrf = "csrf",
            )
        )
        val watcher = createWatcher()

        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:alpha:0001:0001:66D00001:qmcreate:100:root@pam:", "type" to "qmcreate", "id" to "100")
        ))
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:alpha:0002:0001:66D00002:qmcreate:101:root@pam:", "type" to "qmcreate", "id" to "101")
        ))

        assertTrue(posted.isEmpty())
    }

    @Test
    fun masterDisabled_recordsUpids_thenLaterEnableDoesNotDumpBacklog() = runTest {
        sessionStore.updateNotificationPrefs { it.copy(masterEnabled = false) }
        val watcher = createWatcher()

        val backlogTasks = listOf(
            mapOf("upid" to "UPID:pve:0001:0001:66D00001:qmcreate:100:root@pam:", "type" to "qmcreate", "id" to "100"),
            mapOf("upid" to "UPID:pve:0002:0001:66D00002:vzdump:101:root@pam:", "type" to "vzdump", "status" to "error"),
        )

        // Poll with master disabled: tracks UPIDs without alerting
        watcher.onClusterTasks(backlogTasks)
        assertTrue(posted.isEmpty())

        // Turn master ON
        sessionStore.updateNotificationPrefs { it.copy(masterEnabled = true) }

        // Same tasks should NOT be posted
        watcher.onClusterTasks(backlogTasks)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun vzdump_nonOkPosts_vzdumpOkDoesNot() = runTest {
        val watcher = createWatcher()

        // Seed
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0000:0001:66D00000:vzdump:99:root@pam:", "type" to "vzdump", "status" to "OK")
        ))

        // Second poll with OK and non-OK
        val tasks = listOf(
            mapOf("upid" to "UPID:pve:0001:0001:66D00001:vzdump:100:root@pam:", "type" to "vzdump", "id" to "100", "status" to "OK"),
            mapOf("upid" to "UPID:pve:0002:0001:66D00002:vzdump:101:root@pam:", "type" to "vzdump", "id" to "101", "status" to "backup failed: disk full", "node" to "pve"),
        )
        watcher.onClusterTasks(tasks)

        assertEquals(1, posted.size)
        val (_, title, text) = posted[0]
        assertEquals("Backup failed (101)", title)
        assertTrue(text.contains("disk full"))
    }

    @Test
    fun vzdump_runningThenFailed_postsOnce() = runTest {
        val watcher = createWatcher()
        val upid = "UPID:pve:0003:0001:66D00003:vzdump:102:root@pam:"

        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0000:0001:66D00000:vzdump:99:root@pam:", "type" to "vzdump", "status" to "OK"),
        ))
        assertTrue(posted.isEmpty())

        watcher.onClusterTasks(listOf(
            mapOf("upid" to upid, "type" to "vzdump", "id" to "102", "status" to "running", "node" to "pve"),
        ))
        assertTrue(posted.isEmpty())

        watcher.onClusterTasks(listOf(
            mapOf("upid" to upid, "type" to "vzdump", "id" to "102", "status" to "backup failed: disk full", "node" to "pve"),
        ))
        assertEquals(1, posted.size)
        assertEquals("Backup failed (102)", posted[0].second)

        watcher.onClusterTasks(listOf(
            mapOf("upid" to upid, "type" to "vzdump", "id" to "102", "status" to "backup failed: disk full", "node" to "pve"),
        ))
        assertEquals(1, posted.size)
    }

    @Test
    fun qmigrate_triggersLiveUpdateWatchTask() = runTest {
        val watcher = createWatcher()

        // Seed
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0000:0001:66D00000:qmcreate:99:root@pam:", "type" to "qmcreate", "id" to "99")
        ))

        // Migration task
        val migrateUpid = "UPID:pve:0001:0001:66D00001:qmigrate:100:root@pam:"
        watcher.onClusterTasks(listOf(
            mapOf("upid" to migrateUpid, "type" to "qmigrate", "id" to "100", "node" to "pve")
        ))

        assertTrue(recordingPoster.calls.any { it.action == "postOngoing" })
    }

    @Test
    fun localRecentAction_suppressesEcho() = runTest {
        val watcher = createWatcher()

        // Seed
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0000:0001:66D00000:qmcreate:99:root@pam:", "type" to "qmcreate", "id" to "99")
        ))

        // Record recent action for qmcreate on 100
        recentActionRegistry.record("qmcreate", 100L)

        // Remote task arrives for same action & vmid
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0001:0001:66D00001:qmcreate:100:root@pam:", "type" to "qmcreate", "id" to "100", "node" to "pve")
        ))

        // Must be suppressed
        assertTrue(posted.isEmpty())
    }

    @Test
    fun extractNameAndSource_resolvesFixturesCorrectly() {
        // VM with cdrom ISO
        val vmConfig = mapOf(
            "name" to "nova",
            "ide2" to "local:iso/ubuntu.iso,media=cdrom",
        )
        val (vmName, vmSource) = ClusterTaskNotificationWatcher.extractNameAndSource(vmConfig, 100L)
        assertEquals("nova", vmName)
        assertEquals("local:iso/ubuntu.iso", vmSource)

        // CT with ostemplate
        val ctConfig = mapOf(
            "hostname" to "nebula",
            "ostemplate" to "local:vztmpl/debian-12.tar.zst",
        )
        val (ctName, ctSource) = ClusterTaskNotificationWatcher.extractNameAndSource(ctConfig, 200L)
        assertEquals("nebula", ctName)
        assertEquals("local:vztmpl/debian-12.tar.zst", ctSource)

        // Null config fallback
        val (fallbackName, fallbackSource) = ClusterTaskNotificationWatcher.extractNameAndSource(null, 300L)
        assertEquals("Guest 300", fallbackName)
        assertEquals(null, fallbackSource)
    }

    @Test
    fun externalStartStop_vzstartAndVzstopPostWhenEnabledAndNoLocalRecord() = runTest {
        sessionStore.updateNotificationPrefs { it.copy(masterEnabled = true, externalStartStop = true) }
        val watcher = createWatcher()

        // Seed
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0000:0001:66D00000:qmcreate:99:root@pam:", "type" to "qmcreate", "id" to "99")
        ))
        assertTrue(posted.isEmpty())

        // vzstart for VMID 200 with no local record
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0001:0001:66D00001:vzstart:200:root@pam:", "type" to "vzstart", "id" to "200", "node" to "pve", "status" to "OK")
        ))
        assertEquals(1, posted.size)
        val (_, title1, body1) = posted[0]
        assertEquals("External guest event (200)", title1)
        assertEquals("Node pve · vzstart (OK)", body1)

        // vzstop for VMID 201 with no local record
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0002:0001:66D00002:vzstop:201:root@pam:", "type" to "vzstop", "id" to "201", "node" to "pve", "status" to "OK")
        ))
        assertEquals(2, posted.size)
        val (_, title2, body2) = posted[1]
        assertEquals("External guest event (201)", title2)
        assertEquals("Node pve · vzstop (OK)", body2)
    }

    @Test
    fun externalStartStop_localPctstartSuppressesVzstartEcho() = runTest {
        sessionStore.updateNotificationPrefs { it.copy(masterEnabled = true, externalStartStop = true) }
        val watcher = createWatcher()

        // Seed
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0000:0001:66D00000:qmcreate:99:root@pam:", "type" to "qmcreate", "id" to "99")
        ))
        assertTrue(posted.isEmpty())

        // Local pctstart recorded for container 300
        recentActionRegistry.record("pctstart", 300L)

        // Incoming vzstart task for container 300
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0003:0001:66D00003:vzstart:300:root@pam:", "type" to "vzstart", "id" to "300", "node" to "pve", "status" to "OK")
        ))

        // Suppressed due to local action
        assertTrue(posted.isEmpty())
    }

    @Test
    fun defaultNotificationPrefs_externalStartStopIsFalse() = runTest {
        assertFalse(sessionStore.notificationPrefs.value.externalStartStop)
        val watcher = createWatcher()

        // Seed
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0000:0001:66D00000:qmcreate:99:root@pam:", "type" to "qmcreate", "id" to "99")
        ))

        // vzstart arrives with default prefs (externalStartStop = false) -> should not post
        watcher.onClusterTasks(listOf(
            mapOf("upid" to "UPID:pve:0001:0001:66D00001:vzstart:200:root@pam:", "type" to "vzstart", "id" to "200", "node" to "pve", "status" to "OK")
        ))
        assertTrue(posted.isEmpty())
    }
}
