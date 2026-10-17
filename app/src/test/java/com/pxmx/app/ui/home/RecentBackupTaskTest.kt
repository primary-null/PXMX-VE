package com.pxmx.app.ui.home

import com.pxmx.app.data.repo.recentBackupChip
import com.pxmx.app.data.repo.recentBackupDetail
import com.pxmx.app.data.repo.taskStatusChip
import com.pxmx.app.data.repo.taskStatusDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentBackupTaskTest {

    @Test
    fun testOkStatusYieldsOkChipAndNullDetail() {
        assertEquals("OK", recentBackupChip("OK"))
        assertEquals("OK", recentBackupChip("ok"))
        assertEquals("OK", recentBackupChip("Ok"))
        assertNull(recentBackupDetail("OK"))
        assertNull(recentBackupDetail("ok"))

        val task = RecentBackupTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            guestId = "100",
            node = "node1",
            status = "OK",
            startTime = 1700000000L,
        )
        assertEquals("OK", task.chip)
        assertNull(task.detail)
    }

    @Test
    fun testRunningStatusYieldsRunningChipAndNullDetail() {
        assertEquals("RUNNING", recentBackupChip("running"))
        assertEquals("RUNNING", recentBackupChip("RUNNING"))
        assertEquals("RUNNING", recentBackupChip("Running"))
        assertNull(recentBackupDetail("running"))
        assertNull(recentBackupDetail("RUNNING"))

        val task = RecentBackupTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            guestId = "100",
            node = "node1",
            status = "running",
            startTime = 1700000000L,
        )
        assertEquals("RUNNING", task.chip)
        assertNull(task.detail)
    }

    @Test
    fun testWrongContentTypeFixtureYieldsFailedChipAndHumanizedSentence() {
        val rawStatus = "could not get storage information for 'vm-disks': " +
            "can't use storage 'vm-disks' for backups - wrong content type"
        val expectedDetail = "That datastore does not hold backups. Pick one whose content includes backup."

        assertEquals("FAILED", recentBackupChip(rawStatus))
        assertEquals(expectedDetail, recentBackupDetail(rawStatus))

        val task = RecentBackupTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            guestId = "100",
            node = "node1",
            status = rawStatus,
            startTime = 1700000000L,
        )
        assertEquals("FAILED", task.chip)
        assertEquals(expectedDetail, task.detail)
    }

    @Test
    fun testUnmatchedErrorYieldsFailedChipAndErrorString() {
        val rawStatus = "command failed (exit code 2)"

        assertEquals("FAILED", recentBackupChip(rawStatus))
        assertEquals(rawStatus, recentBackupDetail(rawStatus))

        val task = RecentBackupTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            guestId = "100",
            node = "node1",
            status = rawStatus,
            startTime = 1700000000L,
        )
        assertEquals("FAILED", task.chip)
        assertEquals(rawStatus, task.detail)
    }

    @Test
    fun testBlankOrNullStatusYieldsFailedChip() {
        assertEquals("FAILED", recentBackupChip(""))
        assertEquals("FAILED", recentBackupChip("   "))
        assertEquals("FAILED", recentBackupChip(null))
        assertEquals("FAILED", recentBackupChip("error"))

        assertEquals("Failed", recentBackupDetail(""))
        assertEquals("Failed", recentBackupDetail("   "))
        assertEquals("Failed", recentBackupDetail(null))
        assertEquals("error", recentBackupDetail("error"))

        val emptyTask = RecentBackupTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            guestId = "100",
            node = "node1",
            status = "",
            startTime = 1700000000L,
        )
        assertEquals("FAILED", emptyTask.chip)
        assertEquals("Failed", emptyTask.detail)
    }

    @Test
    fun testShowsRecentBackupsMatrix() {
        assertTrue(ResourceFilter.ALL.showsRecentBackups())
        assertTrue(ResourceFilter.GUESTS.showsRecentBackups())
        assertFalse(ResourceFilter.NODES.showsRecentBackups())
        assertFalse(ResourceFilter.STORAGE.showsRecentBackups())
    }

    @Test
    fun testParseVzdumpTasksYieldsFailedChipAndFiltersNonVzdump() {
        val longStatus = "could not get storage information for 'vm-disks': " +
            "can't use storage 'vm-disks' for backups - wrong content type"
        val expectedDetail = "That datastore does not hold backups. Pick one whose content includes backup."

        val rawTasks = listOf(
            mapOf(
                "upid" to "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
                "type" to "vzdump",
                "id" to "100",
                "node" to "node1",
                "status" to longStatus,
                "starttime" to 1700000000L,
            ),
            mapOf(
                "upid" to "UPID:node1:00001235:00000000:66D1B000:qmsnapshot:100:root@pam:",
                "type" to "qmsnapshot",
                "id" to "100",
                "node" to "node1",
                "status" to "OK",
                "starttime" to 1700000001L,
            ),
        )

        val parsed = HomeViewModel.parseVzdumpTasks(rawTasks)
        assertEquals(1, parsed.size)

        val task = parsed.first()
        assertEquals("100", task.guestId)
        assertEquals("node1", task.node)
        assertEquals(longStatus, task.status)
        assertEquals("FAILED", task.chip)
        assertNotEquals(longStatus, task.chip)
        assertEquals(expectedDetail, task.detail)
    }

    @Test
    fun testSharedChipHelperTasksStyleStatus() {
        assertEquals("FAILED", taskStatusChip("connection timed out"))
        assertEquals("connection timed out", taskStatusDetail("connection timed out"))

        assertEquals("OK", taskStatusChip("OK"))
        assertNull(taskStatusDetail("OK"))

        assertEquals("RUNNING", taskStatusChip("running"))
        assertNull(taskStatusDetail("running"))
    }
}

