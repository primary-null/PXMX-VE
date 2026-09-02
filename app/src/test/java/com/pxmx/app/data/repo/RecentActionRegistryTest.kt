package com.pxmx.app.data.repo

import com.pxmx.app.data.model.ClusterLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentActionRegistryTest {

    @Test
    fun isSuppressed_suppressesMatchingQuickActionWithinTtl() {
        val registry = RecentActionRegistry(ttlMs = 15_000L)
        val now = 1_000_000L

        // Record guest start action for VM 100
        registry.record("qmstart", 100L, nowMs = now)

        val matchingEntry = ClusterLogEntry(
            id = "UPID:alpha:00001000:00000000:66D1B000:qmstart:100:root@pam:",
            time = now / 1000,
            node = "alpha",
            tag = "qmstart",
            msg = "starting VM 100",
            pri = 6,
            user = "root@pam",
        )

        // 1. Within TTL and same user -> suppressed
        assertTrue(registry.isSuppressed(matchingEntry, sessionUser = "root@pam", nowMs = now + 5_000L))

        // 2. Different user (e.g. admin@pam) -> NOT suppressed
        assertFalse(registry.isSuppressed(matchingEntry, sessionUser = "admin@pam", nowMs = now + 5_000L))

        // 3. Different VM ID -> NOT suppressed
        val differentVmEntry = matchingEntry.copy(msg = "starting VM 200", id = "UPID:...:qmstart:200:root@pam:")
        assertFalse(registry.isSuppressed(differentVmEntry, sessionUser = "root@pam", nowMs = now + 5_000L))

        // 4. Past TTL (16s later) -> NOT suppressed
        assertFalse(registry.isSuppressed(matchingEntry, sessionUser = "root@pam", nowMs = now + 16_000L))
    }

    @Test
    fun isSuppressed_longJobsAreNeverSuppressed() {
        val registry = RecentActionRegistry(ttlMs = 15_000L)
        val now = 1_000_000L

        registry.record("vzdump", 100L, nowMs = now)
        registry.record("aptupdate", 100L, nowMs = now)

        val backupEntry = ClusterLogEntry(
            id = "UPID:alpha:00001001:00000000:66D1B001:vzdump:100:root@pam:",
            time = now / 1000,
            node = "alpha",
            tag = "vzdump",
            msg = "backup successful for VM 100",
            pri = 6,
            user = "root@pam",
        )
        assertFalse(registry.isSuppressed(backupEntry, sessionUser = "root@pam", nowMs = now + 1_000L))

        val aptEntry = ClusterLogEntry(
            id = "UPID:alpha:00001002:00000000:66D1B002:aptupdate:100:root@pam:",
            time = now / 1000,
            node = "alpha",
            tag = "aptupdate",
            msg = "updating package lists",
            pri = 6,
            user = "root@pam",
        )
        assertFalse(registry.isSuppressed(aptEntry, sessionUser = "root@pam", nowMs = now + 1_000L))
    }

    @Test
    fun filterLatestLogForStrip_selectsPreviousNonSuppressedLine() {
        val registry = RecentActionRegistry(ttlMs = 15_000L)
        val now = 1_000_000L

        registry.record("qmstart", 100L, nowMs = now)

        val entry1Suppressed = ClusterLogEntry(
            id = "log-1",
            time = now / 1000,
            node = "alpha",
            tag = "qmstart",
            msg = "starting VM 100",
            pri = 6,
            user = "root@pam",
        )
        val entry2Syslog = ClusterLogEntry(
            id = "log-2",
            time = (now - 10_000L) / 1000,
            node = "alpha",
            tag = "systemd",
            msg = "Started apt-daily.timer",
            pri = 6,
            user = "root@pam",
        )

        val entries = listOf(entry1Suppressed, entry2Syslog)

        // Newest entry (entry1) is suppressed, so strip displays entry2
        val chosen = filterLatestLogForStrip(
            entries = entries,
            recentRegistry = registry,
            sessionUser = "root@pam",
            nowMs = now + 2_000L,
        )
        assertNotNull(chosen)
        assertEquals("log-2", chosen?.id)
        assertEquals("Started apt-daily.timer", chosen?.msg)
    }

    @Test
    fun filterLatestLogForStrip_guestDetailScoping() {
        val registry = RecentActionRegistry()
        val now = 1_000_000L

        val entryVm100 = ClusterLogEntry(
            id = "log-100",
            time = now / 1000,
            node = "alpha",
            tag = "systemd",
            msg = "guest 100 network interface up",
            pri = 6,
            user = "root@pam",
        )
        val entryVm200 = ClusterLogEntry(
            id = "log-200",
            time = now / 1000,
            node = "beta",
            tag = "systemd",
            msg = "guest 200 backup finished",
            pri = 6,
            user = "root@pam",
        )

        val entries = listOf(entryVm200, entryVm100)

        // Scoped to VM 100
        val chosenFor100 = filterLatestLogForStrip(
            entries = entries,
            recentRegistry = registry,
            sessionUser = "root@pam",
            targetVmid = 100L,
            nowMs = now,
        )
        assertEquals("log-100", chosenFor100?.id)

        // Scoped to non-existent VM 999
        val chosenFor999 = filterLatestLogForStrip(
            entries = entries,
            recentRegistry = registry,
            sessionUser = "root@pam",
            targetVmid = 999L,
            nowMs = now,
        )
        assertNull(chosenFor999)
    }

    @Test
    fun filterLatestLogForStrip_updatesScreenExcludesAptStreaming() {
        val registry = RecentActionRegistry()
        val now = 1_000_000L

        val aptUpgradeLine = ClusterLogEntry(
            id = "task-apt-1",
            time = now / 1000,
            node = "alpha",
            tag = "apt-upgrade",
            msg = "Unpacking pve-manager...",
            pri = 6,
            user = "root@pam",
        )
        val regularSyslog = ClusterLogEntry(
            id = "syslog-1",
            time = (now - 5000L) / 1000,
            node = "beta",
            tag = "pmxcfs",
            msg = "cluster configuration synchronized",
            pri = 6,
            user = "root@pam",
        )

        val entries = listOf(aptUpgradeLine, regularSyslog)

        // When updates screen is active -> apt-upgrade line is filtered out of strip
        val whenUpdatesActive = filterLatestLogForStrip(
            entries = entries,
            recentRegistry = registry,
            sessionUser = "root@pam",
            isUpdatesScreenActive = true,
            nowMs = now,
        )
        assertEquals("syslog-1", whenUpdatesActive?.id)

        // When elsewhere -> apt-upgrade line is shown
        val whenElsewhere = filterLatestLogForStrip(
            entries = entries,
            recentRegistry = registry,
            sessionUser = "root@pam",
            isUpdatesScreenActive = false,
            nowMs = now,
        )
        assertEquals("task-apt-1", whenElsewhere?.id)
    }
}
