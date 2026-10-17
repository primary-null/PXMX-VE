package com.pxmx.app.ui.watch

import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.WatchIds
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchModeTest {

    @Test
    fun guestAndNodeIds_storageIsNotWatchable() {
        val guest = ClusterResource(type = "qemu", node = "pve", vmid = 100, name = "web")
        val ct = ClusterResource(type = "lxc", node = "pve", vmid = 200, name = "dns")
        val node = ClusterResource(type = "node", node = "pve", id = "node/pve")
        val storage = ClusterResource(type = "storage", node = "pve", storage = "local")
        val nameless = ClusterResource(type = "qemu", vmid = 100)

        assertEquals("guest|pve|100", WatchIds.of(guest))
        assertEquals("guest|pve|200", WatchIds.of(ct))
        assertEquals("node|pve", WatchIds.of(node))
        assertNull(WatchIds.of(storage))
        assertNull(WatchIds.of(nameless))
        assertTrue(WatchIds.isValid("guest|pve|100"))
        assertTrue(WatchIds.isValid("node|pve"))
        assertFalse(WatchIds.isValid("storage|pve|local"))
        assertFalse(WatchIds.isValid("guest||100"))
    }

    @Test
    fun notificationIdStaysInTheWatchRange() {
        val first = WatchIds.notificationId("guest|pve|100")
        val again = WatchIds.notificationId("guest|pve|100")
        assertEquals(first, again)
        assertTrue(first >= WatchIds.NOTIFICATION_ID_BASE)
        assertTrue(first <= WatchIds.NOTIFICATION_ID_BASE + 0xFFFF)
        assertTrue(WatchIds.notificationId("node|pve") >= WatchIds.NOTIFICATION_ID_BASE)
    }

    @Test
    fun noticeIncludesTheFieldsOnTheResource() {
        val guest = ClusterResource(
            type = "qemu",
            node = "pve",
            vmid = 100,
            name = "web01",
            status = "running",
            cpu = 0.4,
            maxcpu = 4,
            mem = 2_000_000_000L,
            maxmem = 8_000_000_000L,
            disk = 10_000_000_000L,
            maxdisk = 50_000_000_000L,
            uptime = 9_000L,
            tags = "prod,web",
            hastate = "started",
            pool = "apps",
            ostype = "l26",
            onboot = 1,
            netin = 1_024L,
            netout = 2_048L,
        )
        val notice = watchNotice(guest)
        assertEquals("WATCH · web01", notice?.title)
        val body = notice?.bigText.orEmpty()
        assertTrue(body.contains("VM · pve · VMID 100"))
        assertTrue(body.contains("status running"))
        assertTrue(body.contains("CPU ${formatPercent(40.0)}"))
        assertTrue(body.contains("MEM ${formatBytes(2_000_000_000L)}"))
        assertTrue(body.contains("DISK ${formatBytes(10_000_000_000L)}"))
        assertTrue(body.contains("up "))
        assertTrue(body.contains("tags prod,web"))
        assertTrue(body.contains("HA started"))
        assertTrue(body.contains("pool apps"))
        assertTrue(body.contains("os l26"))
        assertTrue(body.contains("vcpu 4"))
        assertTrue(body.contains("onboot yes"))
        assertTrue(body.contains("net in ${formatBytes(1_024L)}"))
        assertTrue(notice?.text.orEmpty().contains("status running"))
        assertNull(watchNotice(ClusterResource(type = "storage", node = "pve", storage = "local")))
    }

    @Test
    fun nodeNoticeUsesTheNodeName() {
        val node = ClusterResource(
            type = "node",
            node = "pve",
            id = "node/pve",
            status = "online",
            cpu = 0.1,
            hastate = "online",
        )
        val notice = watchNotice(node)
        assertEquals("WATCH · PVE", notice?.title)
        assertTrue(notice?.bigText.orEmpty().contains("NODE · pve"))
        assertTrue(notice?.bigText.orEmpty().contains("HA online"))
    }

    @Test
    fun watchedIdsRoundTripAndRejectJunk() {
        val prefs = FakeSharedPreferences()
        val store = SessionStore(injectedPrefs = prefs)
        store.setWatched("storage|local", true)
        store.setWatched("guest|pve|100", true)
        store.setWatched("node|pve", true)
        assertEquals(setOf("guest|pve|100", "node|pve"), store.watchedIds.value)

        store.setWatched("guest|pve|100", false)
        assertEquals(setOf("node|pve"), store.watchedIds.value)

        val reloaded = SessionStore(injectedPrefs = prefs)
        assertEquals(setOf("node|pve"), reloaded.watchedIds.value)

        reloaded.purgeAll()
        assertTrue(reloaded.watchedIds.value.isEmpty())
        assertTrue(SessionStore(injectedPrefs = prefs).watchedIds.value.isEmpty())
    }
}
