package com.pxmx.app.ui.guest

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuestDetailViewModelTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProxmoxRepository
    private lateinit var demoApi: DemoApi
    private lateinit var vm: GuestDetailViewModel
    private var collectJob: Job? = null

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        demoApi = DemoApi()
        val fakePrefs = FakeSharedPreferences()
        sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "192.168.1.10", port = 8006),
                ticket = "ticket",
                csrf = "csrf",
            )
        )
        val apiProvider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = demoApi
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demoApi)
            override fun clear() {}
        }
        repository = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = apiProvider,
        )
        vm = GuestDetailViewModel(
            repository = repository,
            node = "pve1",
            guestType = GuestType.QEMU,
            vmid = 100L,
            name = "web01",
        )
        collectJob = CoroutineScope(UnconfinedTestDispatcher()).launch {
            vm.ui.collect()
        }
    }

    @After
    fun tearDown() {
        collectJob?.cancel()
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 1. Initial State & Accordion Section Navigation
    // -------------------------------------------------------------------------

    @Test
    fun initialState_defaultsToHardwareExpanded() {
        val state = vm.ui.value

        assertEquals("pve1", state.node)
        assertEquals(GuestType.QEMU, state.guestType)
        assertEquals(100L, state.vmid)
        assertEquals("nova", state.name)
        assertEquals(GuestSection.HARDWARE, state.expanded)
    }

    @Test
    fun toggleSection_whenAlreadyExpanded_collapsesToNull() {
        // HARDWARE is expanded by default; clicking it collapses
        vm.toggleSection(GuestSection.HARDWARE)
        assertNull(vm.ui.value.expanded)

        // Clicking it again re-expands
        vm.toggleSection(GuestSection.HARDWARE)
        assertEquals(GuestSection.HARDWARE, vm.ui.value.expanded)
    }

    @Test
    fun toggleSection_whenDifferentSection_expandsTargetSection() {
        vm.toggleSection(GuestSection.SNAPSHOTS)
        assertEquals(GuestSection.SNAPSHOTS, vm.ui.value.expanded)

        vm.toggleSection(GuestSection.BACKUPS)
        assertEquals(GuestSection.BACKUPS, vm.ui.value.expanded)
    }

    @Test
    fun selectSection_setsExpandedDirectly() {
        vm.selectSection(GuestSection.OPTIONS)
        assertEquals(GuestSection.OPTIONS, vm.ui.value.expanded)

        vm.selectSection(null)
        assertNull(vm.ui.value.expanded)
    }

    // -------------------------------------------------------------------------
    // 2. Dialog and Modal State Toggles
    // -------------------------------------------------------------------------

    @Test
    fun dialogToggles_openCreateSnapshot_and_openCreateBackup() {
        vm.openCreateSnapshot(true)
        assertTrue(vm.ui.value.showCreateSnapshot)
        vm.openCreateSnapshot(false)
        assertFalse(vm.ui.value.showCreateSnapshot)

        vm.openCreateBackup(true)
        assertTrue(vm.ui.value.showCreateBackup)
        vm.openCreateBackup(false)
        assertFalse(vm.ui.value.showCreateBackup)
    }

    @Test
    fun confirmationModals_confirmDeleteAndRollback_setsTargetNames() {
        vm.confirmDeleteSnapshot("snap_initial")
        assertEquals("snap_initial", vm.ui.value.confirmDeleteSnap)
        vm.confirmDeleteSnapshot(null)
        assertNull(vm.ui.value.confirmDeleteSnap)

        vm.confirmRollback("snap_rollback")
        assertEquals("snap_rollback", vm.ui.value.confirmRollbackSnap)
        vm.confirmRollback(null)
        assertNull(vm.ui.value.confirmRollbackSnap)

        val backup = BackupVolume(volid = "local:backup/vzdump-qemu-100-test.vma.zst", ctime = 1700000000L)
        vm.confirmDeleteBackup(backup)
        assertEquals(backup, vm.ui.value.confirmDeleteBackup)
        vm.confirmDeleteBackup(null)
        assertNull(vm.ui.value.confirmDeleteBackup)
    }

    // -------------------------------------------------------------------------
    // 3. Validation on Snapshot and Backup Creation
    // -------------------------------------------------------------------------

    @Test
    fun createSnapshot_blankOrCurrentName_setsError() {
        vm.createSnapshot("", "empty name", false)
        assertEquals("Invalid snapshot name", vm.ui.value.error)

        vm.createSnapshot("current", "reserved name", false)
        assertEquals("Invalid snapshot name", vm.ui.value.error)
    }

    @Test
    fun createBackup_emptyStorage_setsError() {
        vm.createBackup("", "snapshot")
        assertEquals("Select a backup storage", vm.ui.value.error)
    }

    // -------------------------------------------------------------------------
    // 4. Bundle Loading
    // -------------------------------------------------------------------------

    @Test
    fun refresh_populatesStateFromDemoApi() {
        val state = vm.ui.value
        assertFalse(state.loading)
        assertNotNull(state.status)
        assertNotNull(state.config)
        assertNotNull(state.backups)
        assertNotNull(state.snapshots)
        assertEquals("nova", state.status?.name)
    }

    // -------------------------------------------------------------------------
    // 5. Action Strip Debouncing
    // -------------------------------------------------------------------------

    @Test
    fun runPowerAction_whenActionInProgress_debouncesAndRejectsSecondaryInvocation() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val blockingApi = object : ProxmoxApi by demoApi {
            override suspend fun guestAction(node: String, type: String, vmid: Long, action: String): com.pxmx.app.data.model.PveResponse<String> {
                gate.await()
                return demoApi.guestAction(node, type, vmid, action)
            }
        }
        val customRepo = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = object : ProxmoxApiProvider {
                override fun apiFor(config: ServerConfig): ProxmoxApi = blockingApi
                override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(blockingApi)
                override fun clear() {}
            },
        )
        val testVm = GuestDetailViewModel(customRepo, "pve1", GuestType.QEMU, 100L, "web01")
        val job = launch(UnconfinedTestDispatcher()) { testVm.ui.collect() }

        // Trigger first action — suspends waiting for gate
        testVm.runPowerAction(com.pxmx.app.data.model.GuestAction.START)
        assertEquals("power:start", testVm.ui.value.actionInProgress)

        // Secondary action while first is in progress is debounced and rejected
        testVm.runPowerAction(com.pxmx.app.data.model.GuestAction.REBOOT)
        assertEquals("power:start", testVm.ui.value.actionInProgress)

        // Release gate and clean up
        gate.complete(Unit)
        job.cancel()
    }
}
