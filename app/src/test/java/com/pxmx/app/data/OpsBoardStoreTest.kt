package com.pxmx.app.data

import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.ops.InMemoryOpsBoardStore
import com.pxmx.app.data.ops.OpsBoard
import com.pxmx.app.data.ops.OpsBoardStore
import com.pxmx.app.data.ops.computeAppliedKeys
import com.pxmx.app.data.ops.guestDetailLazyKeys
import com.pxmx.app.ui.guest.GuestSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsBoardStoreTest {

    @Test
    fun defaultBoardKeysMatchOpsScanDeck() {
        val board = OpsBoard.DEFAULT
        val keys = computeAppliedKeys(board, isTourActive = false)
        val expected = listOf(
            "hero",
            "hardware",
            "network",
            "firewall",
            "snapshots",
            "backups",
            "options",
            "console",
            "history-graph",
            "raw",
        )
        assertEquals(expected, keys)
    }

    @Test
    fun pinSnapshotsMovesSnapshotsBeforeNetwork() {
        val board = OpsBoard.DEFAULT.pinSection(GuestSection.SNAPSHOTS)
        assertTrue(board.isPinned(GuestSection.SNAPSHOTS))
        val keys = computeAppliedKeys(board, isTourActive = false)
        val snapIdx = keys.indexOf("snapshots")
        val netIdx = keys.indexOf("network")
        assertTrue("snapshots should be present", snapIdx >= 0)
        assertTrue("network should be present", netIdx >= 0)
        assertTrue("snapshots ($snapIdx) must appear before network ($netIdx)", snapIdx < netIdx)
    }

    @Test
    fun toggleHistoryFalseOmitsHistoryGraph() {
        val board = OpsBoard.DEFAULT.toggleHistory(false)
        assertFalse(board.includeHistory)
        val keys = computeAppliedKeys(board, isTourActive = false)
        assertFalse("history-graph should be omitted", keys.contains("history-graph"))
        assertTrue("console should still be present", keys.contains("console"))
    }

    @Test
    fun rawNeverEntersPinnedSections() {
        val board = OpsBoard.DEFAULT.pinSection(GuestSection.RAW)
        assertFalse(board.isPinned(GuestSection.RAW))
        assertFalse(board.pinnedSections.contains("RAW"))
        val keys = computeAppliedKeys(board, isTourActive = false)
        assertEquals("raw", keys.last())
    }

    @Test
    fun demoProfileDoesNotResolveRealProfileDefault() = runBlocking {
        val store = InMemoryOpsBoardStore()
        val realBoard = OpsBoard.DEFAULT.pinSection(GuestSection.FIREWALL)
        store.setProfileDefault("real-profile", realBoard)

        val demoBoard = store.getBoard("demo", GuestType.QEMU, 100L)
        assertFalse("demo should not have firewall pinned", demoBoard.isPinned(GuestSection.FIREWALL))
        assertEquals(OpsBoard.DEFAULT, demoBoard)

        val realResolved = store.getBoard("real-profile", GuestType.QEMU, 100L)
        assertTrue("real profile should have firewall pinned", realResolved.isPinned(GuestSection.FIREWALL))
    }

    @Test
    fun setProfileDefaultThenClearGuestBoardUsesDefault() = runBlocking {
        val store = InMemoryOpsBoardStore()
        val profileDefault = OpsBoard.DEFAULT.pinSection(GuestSection.BACKUPS)
        val guestOverride = OpsBoard.DEFAULT.pinSection(GuestSection.SNAPSHOTS)

        store.setProfileDefault("p1", profileDefault)
        store.saveGuestBoard("p1", GuestType.QEMU, 100L, guestOverride)

        val beforeClear = store.getBoard("p1", GuestType.QEMU, 100L)
        assertTrue(beforeClear.isPinned(GuestSection.SNAPSHOTS))
        assertFalse(beforeClear.isPinned(GuestSection.BACKUPS))

        store.clearGuestBoard("p1", GuestType.QEMU, 100L)

        val afterClear = store.getBoard("p1", GuestType.QEMU, 100L)
        assertFalse(afterClear.isPinned(GuestSection.SNAPSHOTS))
        assertTrue(afterClear.isPinned(GuestSection.BACKUPS))
    }

    @Test
    fun clearAndPurgeEmptiesPayload() = runBlocking {
        val store = InMemoryOpsBoardStore()
        store.setProfileDefault("p1", OpsBoard.DEFAULT.pinSection(GuestSection.HARDWARE))
        store.saveGuestBoard("p1", GuestType.QEMU, 100L, OpsBoard.DEFAULT.pinSection(GuestSection.NETWORK))
        store.setPinHintShown(true)

        val payload = store.data.first()
        assertTrue(payload.profileDefaults.isNotEmpty())
        assertTrue(payload.guestOverrides.isNotEmpty())
        assertTrue(payload.pinHintShown)

        store.clear()
        val cleared = store.data.first()
        assertTrue(cleared.profileDefaults.isEmpty())
        assertTrue(cleared.guestOverrides.isEmpty())
        assertFalse(cleared.pinHintShown)

        store.setProfileDefault("p2", OpsBoard.DEFAULT)
        store.purge()
        val purged = store.data.first()
        assertTrue(purged.profileDefaults.isEmpty())
    }

    @Test
    fun pinCap4IgnoresFifthPin() {
        var board = OpsBoard.DEFAULT
        board = board.pinSection(GuestSection.HARDWARE)
        board = board.pinSection(GuestSection.NETWORK)
        board = board.pinSection(GuestSection.FIREWALL)
        board = board.pinSection(GuestSection.SNAPSHOTS)
        assertEquals(4, board.pinnedSections.size)

        // Attempt 5th pin
        val fifth = board.pinSection(GuestSection.BACKUPS)
        assertEquals("5th pin should be ignored", 4, fifth.pinnedSections.size)
        assertFalse(fifth.isPinned(GuestSection.BACKUPS))
        assertEquals(board, fifth)
    }

    @Test
    fun overridesCapAt200AndDropOldest() = runBlocking {
        val store = InMemoryOpsBoardStore()
        for (i in 1..205) {
            store.saveGuestBoard("prof", GuestType.QEMU, i.toLong(), OpsBoard.DEFAULT)
        }
        val payload = store.data.first()
        assertEquals(200, payload.guestOverrides.size)
        assertFalse(payload.guestOverrides.containsKey("prof|qemu|1"))
        assertFalse(payload.guestOverrides.containsKey("prof|qemu|5"))
        assertTrue(payload.guestOverrides.containsKey("prof|qemu|6"))
        assertTrue(payload.guestOverrides.containsKey("prof|qemu|205"))
    }

    @Test
    fun guestDetailLazyKeys_notEditing_matchesAppliedKeys() {
        val applied = computeAppliedKeys(OpsBoard.DEFAULT, isTourActive = false)
        val lazyKeys = guestDetailLazyKeys(isEditingBoard = false, appliedKeys = applied)
        assertEquals(applied, lazyKeys)
        assertEquals("hero", lazyKeys.first())
        assertEquals(0, lazyKeys.indexOf("hero"))
        assertFalse(lazyKeys.contains("snack"))
    }

    @Test
    fun guestDetailLazyKeys_editing_hasEditBoardFirst() {
        val applied = computeAppliedKeys(OpsBoard.DEFAULT, isTourActive = false)
        val lazyKeys = guestDetailLazyKeys(isEditingBoard = true, appliedKeys = applied)
        assertEquals("edit_board", lazyKeys.first())
        assertEquals("hero", lazyKeys[1])
        assertFalse(lazyKeys.contains("snack"))
    }

    @Test
    fun guestDetailLazyKeys_pinSnapshots_indexOffsetsMatch() {
        val board = OpsBoard.DEFAULT.pinSection(GuestSection.SNAPSHOTS)
        val applied = computeAppliedKeys(board, isTourActive = false)
        val notEditingKeys = guestDetailLazyKeys(isEditingBoard = false, appliedKeys = applied)
        val editingKeys = guestDetailLazyKeys(isEditingBoard = true, appliedKeys = applied)

        val appliedSnapIdx = applied.indexOf("snapshots")
        assertTrue(appliedSnapIdx >= 0)
        assertEquals(appliedSnapIdx, notEditingKeys.indexOf("snapshots"))
        assertEquals(appliedSnapIdx + 1, editingKeys.indexOf("snapshots"))
    }

    @Test
    fun reorderVisibleClip_swapHwBelowNet_persistsOrderAndPreservesInvariants() {
        val board = OpsBoard.DEFAULT.toggleConsole(false)
        val reordered = board.reorderVisibleClip("HW", 1)
        val keys = computeAppliedKeys(reordered, isTourActive = false)
        val hwIdx = keys.indexOf("hardware")
        val netIdx = keys.indexOf("network")
        assertTrue("hardware should be present", hwIdx >= 0)
        assertTrue("network should be present", netIdx >= 0)
        assertTrue("HW should be below NET (netIdx < hwIdx)", netIdx < hwIdx)
        assertEquals("hero", keys.first())
        assertEquals(0, keys.indexOf("hero"))
        assertEquals("raw", keys.last())
        assertFalse("hidden CONSOLE stays hidden", keys.contains("console"))

        // Attempting to move VITALS or RAW is refused
        assertEquals(reordered, reordered.reorderVisibleClip("VITALS", 1))
        assertEquals(reordered, reordered.reorderVisibleClip("VITALS", -1))
        assertEquals(reordered, reordered.reorderVisibleClip("RAW", -1))
        assertEquals(reordered, reordered.reorderVisibleClip("RAW", 1))

        // Movable clip cannot pass VITALS
        val atTop = reordered.reorderVisibleClip("NET", -1)
        assertEquals("Cannot move past VITALS", reordered, atTop)

        // Movable clip cannot pass RAW
        val bottomMovable = keys[keys.size - 2]
        val atBottom = reordered.reorderVisibleClip(bottomMovable, 1)
        assertEquals("Cannot move past RAW", reordered, atBottom)
    }
}

