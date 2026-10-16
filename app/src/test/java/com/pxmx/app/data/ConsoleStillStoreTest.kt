package com.pxmx.app.data

import android.content.ComponentCallbacks2
import com.pxmx.app.data.console.ConsoleStill
import com.pxmx.app.data.console.ConsoleStillStore
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.session.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleStillStoreTest {

    @Test
    fun qemu_putAndGet_returnsStill() {
        val store = ConsoleStillStore.create(null)
        val still = ConsoleStill(
            jpegBytes = byteArrayOf(1, 2, 3),
            timestampEpochMs = 1000L,
        )

        store.putStill("profile-1", GuestType.QEMU, 100L, still)
        val retrieved = store.getStill("profile-1", GuestType.QEMU, 100L)

        assertNotNull(retrieved)
        assertEquals(still, retrieved)
    }

    @Test
    fun lxc_put_isNoOp() {
        val store = ConsoleStillStore.create(null)
        val still = ConsoleStill(
            jpegBytes = byteArrayOf(1, 2, 3),
            timestampEpochMs = 1000L,
        )

        store.putStill("profile-1", GuestType.LXC, 100L, still)
        val retrieved = store.getStill("profile-1", GuestType.LXC, 100L)

        assertNull(retrieved)
        assertTrue(store.stills.value.isEmpty())
    }

    @Test
    fun cap8_lruEviction_removesOldestAccessed() {
        val store = ConsoleStillStore.create(null)

        // Insert 8 stills: vmid 1..8
        for (i in 1..8) {
            store.putStill(
                profileId = "profile-1",
                guestType = GuestType.QEMU,
                vmid = i.toLong(),
                still = ConsoleStill(jpegBytes = byteArrayOf(i.toByte())),
            )
        }
        assertEquals(8, store.stills.value.size)

        // Access 1L, so 2L becomes the oldest accessed
        assertNotNull(store.getStill("profile-1", GuestType.QEMU, 1L))

        // Insert 9th still: vmid 9
        store.putStill(
            profileId = "profile-1",
            guestType = GuestType.QEMU,
            vmid = 9L,
            still = ConsoleStill(jpegBytes = byteArrayOf(9.toByte())),
        )

        assertEquals(8, store.stills.value.size)
        // 2L was oldest and should be evicted
        assertNull(store.getStill("profile-1", GuestType.QEMU, 2L))
        // 1L was refreshed and should still be present
        assertNotNull(store.getStill("profile-1", GuestType.QEMU, 1L))
        // 9L is newly added
        assertNotNull(store.getStill("profile-1", GuestType.QEMU, 9L))
    }

    @Test
    fun clear_emptiesStore() {
        val store = ConsoleStillStore.create(null)
        store.putStill(
            profileId = "profile-1",
            guestType = GuestType.QEMU,
            vmid = 100L,
            still = ConsoleStill(jpegBytes = byteArrayOf(1)),
        )
        assertEquals(1, store.stills.value.size)

        store.clear()
        assertEquals(0, store.stills.value.size)
        assertNull(store.getStill("profile-1", GuestType.QEMU, 100L))
    }

    @Test
    fun purgeAll_withNullContext_doesNotCrash() {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        // Should not throw when context is null
        sessionStore.purgeAll(null)
    }

    @Test
    fun demoKey_isolatedFromRealProfileId() {
        val store = ConsoleStillStore.create(null)
        val still = ConsoleStill(
            jpegBytes = byteArrayOf(42),
            timestampEpochMs = 2000L,
        )

        store.putStill("demo", GuestType.QEMU, 100L, still)

        // Real profileId must not see demo stills
        assertNull(store.getStill("profile-prod", GuestType.QEMU, 100L))
        // Demo host must see its still
        assertNotNull(store.getStill("demo", GuestType.QEMU, 100L))
    }

    @Test
    fun onTrimMemory_clearsOnCriticalCompleteModerate() {
        val store = ConsoleStillStore.create(null)

        fun putSample() {
            store.putStill("profile-1", GuestType.QEMU, 100L, ConsoleStill(byteArrayOf(1)))
        }

        putSample()
        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertTrue(store.stills.value.isEmpty())

        putSample()
        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        assertTrue(store.stills.value.isEmpty())

        putSample()
        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        assertTrue(store.stills.value.isEmpty())

        putSample()
        store.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(1, store.stills.value.size)
    }

    @Test
    fun ageFormatted_returnsExpectedUnits() {
        val now = 1_000_000L
        val still10s = ConsoleStill(jpegBytes = byteArrayOf(1), timestampEpochMs = now - 10_000L)
        assertEquals("10S AGO", still10s.ageFormatted(now))

        val still2m = ConsoleStill(jpegBytes = byteArrayOf(1), timestampEpochMs = now - 120_000L)
        assertEquals("2M AGO", still2m.ageFormatted(now))

        val still2h = ConsoleStill(jpegBytes = byteArrayOf(1), timestampEpochMs = now - 7_200_000L)
        assertEquals("2H AGO", still2h.ageFormatted(now))

        val still2d = ConsoleStill(jpegBytes = byteArrayOf(1), timestampEpochMs = now - 172_800_000L)
        assertEquals("2D AGO", still2d.ageFormatted(now))
    }
}
