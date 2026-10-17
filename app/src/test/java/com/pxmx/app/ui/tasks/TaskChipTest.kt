package com.pxmx.app.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskChipTest {

    @Test
    fun testTaskChipAndDetailForConnectionTimedOut() {
        val task = ClusterTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vncproxy:100:root@pam:",
            node = "node1",
            type = "vncproxy",
            user = "root@pam",
            status = "connection timed out",
            startTime = 1700000000L,
            endTime = 1700000010L,
            pid = 1234L,
        )

        assertEquals("FAILED", task.chip)
        assertEquals("connection timed out", task.detail)
    }

    @Test
    fun testTaskChipAndDetailForOk() {
        val task = ClusterTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:qmstart:100:root@pam:",
            node = "node1",
            type = "qmstart",
            user = "root@pam",
            status = "OK",
            startTime = 1700000000L,
            endTime = 1700000002L,
            pid = 1235L,
        )

        assertEquals("OK", task.chip)
        assertNull(task.detail)
    }

    @Test
    fun testTaskChipAndDetailForRunning() {
        val task = ClusterTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            node = "node1",
            type = "vzdump",
            user = "root@pam",
            status = "running",
            startTime = 1700000000L,
            endTime = null,
            pid = 1236L,
        )

        assertEquals("RUNNING", task.chip)
        assertNull(task.detail)
    }

    @Test
    fun testTaskChipAndDetailForStorageMismatchError() {
        val rawError = "could not get storage information for 'vm-disks': " +
            "can't use storage 'vm-disks' for backups - wrong content type"
        val task = ClusterTask(
            upid = "UPID:node1:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            node = "node1",
            type = "vzdump",
            user = "root@pam",
            status = rawError,
            startTime = 1700000000L,
            endTime = 1700000005L,
            pid = 1237L,
        )

        assertEquals("FAILED", task.chip)
        assertEquals(
            "That datastore does not hold backups. Pick one whose content includes backup.",
            task.detail,
        )
    }
}
