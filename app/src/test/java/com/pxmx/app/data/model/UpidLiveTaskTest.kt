package com.pxmx.app.data.model

import org.junit.Assert.*
import org.junit.Test

class UpidLiveTaskTest {

    @Test
    fun testParseEligibleTypes() {
        val vzdumpUpid = "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:"
        val vzdumpTask = UpidLiveTask.parse(vzdumpUpid)
        assertNotNull(vzdumpTask)
        assertEquals("alpha", vzdumpTask?.node)
        assertEquals("vzdump", vzdumpTask?.type)
        assertEquals("100", vzdumpTask?.id)
        assertEquals("BACKUP 100", vzdumpTask?.title)
        assertEquals("BACKUP 100", vzdumpTask?.displayTitle)
        assertEquals(vzdumpUpid, vzdumpTask?.upid)

        val qmigrateUpid = "UPID:beta:00001235:00000000:66D1B001:qmigrate:105:root@pam:"
        val qmigrateTask = UpidLiveTask.parse(qmigrateUpid)
        assertNotNull(qmigrateTask)
        assertEquals("beta", qmigrateTask?.node)
        assertEquals("qmigrate", qmigrateTask?.type)
        assertEquals("105", qmigrateTask?.id)
        assertEquals("MIGRATE 105", qmigrateTask?.title)

        val vzmigrateUpid = "UPID:gamma:00001236:00000000:66D1B002:vzmigrate:200:admin@pve:"
        val vzmigrateTask = UpidLiveTask.parse(vzmigrateUpid)
        assertNotNull(vzmigrateTask)
        assertEquals("gamma", vzmigrateTask?.node)
        assertEquals("vzmigrate", vzmigrateTask?.type)
        assertEquals("200", vzmigrateTask?.id)
        assertEquals("MIGRATE 200", vzmigrateTask?.title)
    }

    @Test
    fun testParseRejectsIneligibleAndMalformed() {
        // qmstart and qmstop rejected
        assertNull(UpidLiveTask.parse("UPID:gamma:00001237:00000000:66D1B003:qmstart:102:root@pam:"))
        assertNull(UpidLiveTask.parse("UPID:gamma:00001237:00000000:66D1B003:qmstop:102:root@pam:"))

        // Other non-eligible types rejected
        assertNull(UpidLiveTask.parse("UPID:alpha:00001235:00000000:66D1B001:aptupdate::root@pam:"))
        assertNull(UpidLiveTask.parse("UPID:alpha:00003001:00000000:66D1B010:sdnreload::root@pam:"))
        assertNull(UpidLiveTask.parse("UPID:alpha:00001234:00000000:66D1B000:vncproxy:root@pam:"))

        // Empty and blank
        assertNull(UpidLiveTask.parse(""))
        assertNull(UpidLiveTask.parse("   "))
        assertNull(UpidLiveTask.parse(null))

        // Empty type or empty id
        assertNull(UpidLiveTask.parse("UPID:alpha:00001234:00000000:66D1B000::100:root@pam:"))
        assertNull(UpidLiveTask.parse("UPID:alpha:00001234:00000000:66D1B000:vzdump::root@pam:"))

        // Malformed and incomplete
        assertNull(UpidLiveTask.parse("invalid-upid-string"))
        assertNull(UpidLiveTask.parse("UPID:alpha:123"))
    }

    @Test
    fun testTitleNeverContainsUserRealmOrRawUpid() {
        val upids = listOf(
            "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:root@pam:",
            "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:admin@pve:",
            "UPID:alpha:00001234:00000000:66D1B000:vzdump:100:someuser@realm.local:",
            "UPID:beta:00001235:00000000:66D1B001:qmigrate:105:root@pam:",
            "UPID:gamma:00001236:00000000:66D1B002:vzmigrate:200:supersecretuser@pam:",
        )

        for (upid in upids) {
            val task = UpidLiveTask.parse(upid)
            assertNotNull(task)
            val title = task!!.title

            assertFalse("Title must not contain @pam, got: $title", title.contains("@pam"))
            assertFalse("Title must not contain user delimiter '@', got: $title", title.contains("@"))
            assertFalse("Title must not equal raw UPID, got: $title", title == upid)
            assertFalse("Title must not contain raw UPID prefix, got: $title", title.contains("UPID:"))
        }
    }
}
