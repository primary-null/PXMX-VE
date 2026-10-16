package com.pxmx.app.ui.guest

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.HistoryTimeframe
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
        vm = GuestDetailViewModel.Factory(
            repository = repository,
            node = "pve1",
            guestType = GuestType.QEMU,
            vmid = 100L,
            name = "web01",
        ).create(GuestDetailViewModel::class.java)
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
    fun initialState_defaultsToOpsExpanded() {
        val state = vm.ui.value

        assertEquals("pve1", state.node)
        assertEquals(GuestType.QEMU, state.guestType)
        assertEquals(100L, state.vmid)
        assertEquals("nova", state.name)
        assertNull(state.expanded)
    }

    @Test
    fun toggleSection_whenAlreadyExpanded_collapsesToNull() {
        // Start by selecting HARDWARE first
        vm.selectSection(GuestSection.HARDWARE)
        assertEquals(GuestSection.HARDWARE, vm.ui.value.expanded)

        // Toggling HARDWARE collapses to null (OPS)
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

    @Test
    fun rrdTimeframe_hourThenDay() {
        assertEquals(HistoryTimeframe.HOUR, vm.ui.value.rrdTimeframe)
        assertEquals(60, vm.ui.value.rrdData.size)
        assertTrue(vm.ui.value.rrdData.any { it.diskread != null })

        vm.setRrdTimeframe(HistoryTimeframe.DAY)
        assertEquals(HistoryTimeframe.DAY, vm.ui.value.rrdTimeframe)
        assertEquals(48, vm.ui.value.rrdData.size)
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

    @Test
    fun guestFirewall_demoGuest100_hasNonNullSnapshotWithRules() {
        val state = vm.ui.value
        assertNotNull(state.firewall)
        assertTrue(state.firewall!!.enabled)
        assertEquals("guest/qemu/100", state.firewall!!.scope)
        assertTrue(state.firewall!!.rules.isNotEmpty())
        assertEquals(2, state.firewall!!.rules.size)
        assertNull(state.firewallError)
    }

    @Test
    fun guestFirewall_403OnQemuFirewallRules_leavesStatusAndConfigLoaded() {
        val failingApi = object : ProxmoxApi by demoApi {
            override suspend fun qemuFirewallRules(node: String, vmid: Long): com.pxmx.app.data.model.PveResponse<List<Map<String, Any>>> {
                throw com.pxmx.app.data.repo.PveHttpException(403, null, "Permission check failed")
            }
        }
        val customProvider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = failingApi
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(failingApi)
            override fun clear() {}
        }
        val customRepo = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = customProvider,
        )
        val customVm = GuestDetailViewModel.Factory(
            repository = customRepo,
            node = "pve1",
            guestType = GuestType.QEMU,
            vmid = 100L,
            name = "web01",
        ).create(GuestDetailViewModel::class.java)

        val job = CoroutineScope(UnconfinedTestDispatcher()).launch {
            customVm.ui.collect()
        }
        try {
            val state = customVm.ui.value
            // Core data must still load
            assertNotNull(state.status)
            assertNotNull(state.config)
            assertEquals("nova", state.status?.name)
            assertFalse(state.loading)
            // Firewall failed with 403
            assertNull(state.firewall)
            assertNotNull(state.firewallError)
            assertTrue(state.firewallError!!.contains("403"))
        } finally {
            job.cancel()
        }
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
        val testVm = GuestDetailViewModel.Factory(customRepo, "pve1", GuestType.QEMU, 100L, "web01")
            .create(GuestDetailViewModel::class.java)
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

    // -------------------------------------------------------------------------
    // 6. Token vs Ticket Session Distinction (ISSUE-005)
    // -------------------------------------------------------------------------

    @Test
    fun isTokenSession_whenPasswordSession_evaluatesToFalse() {
        assertFalse(vm.ui.value.isTokenSession)
    }

    @Test
    fun isTokenSession_whenApiTokenSession_evaluatesToTrue() {
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(
                    host = "192.168.1.10",
                    port = 8006,
                    authMode = AuthMode.API_TOKEN,
                    apiToken = "user@pam!token=12345",
                ),
            )
        )
        val tokenVm = GuestDetailViewModel.Factory(repository, "pve1", GuestType.QEMU, 100L, "web01")
            .create(GuestDetailViewModel::class.java)
        assertTrue(tokenVm.ui.value.isTokenSession)
    }

    // -------------------------------------------------------------------------
    // 7. Direct Domain Repositories Constructor (No ProxmoxRepository Required)
    // -------------------------------------------------------------------------

    @Test
    fun directConstructor_wiresDomainRepositoriesWithoutProxmoxRepository() {
        val directVm = GuestDetailViewModel(
            guestRepo = repository.guestRepo,
            storageRepo = repository.storageRepo,
            nodeRepo = repository.nodeRepo,
            networkRepo = repository.networkRepo,
            sessionStore = repository.sessionStore,
            recentActionRegistry = repository.recentActionRegistry,
            context = ContextWrapper(null),
            clusterLogCache = repository.clusterLogCache,
            activeAptTask = repository.activeAptTask,
            activeAptLogLine = repository.activeAptLogLine,
            isUpdatesScreenActive = repository.isUpdatesScreenActive,
            node = "pve1",
            guestType = GuestType.QEMU,
            vmid = 100L,
            name = "web01",
        )
        assertEquals(100L, directVm.ui.value.vmid)
        assertEquals("pve1", directVm.ui.value.node)
    }

    @Test
    fun liveInterfaces_demoGuest100_containsExpectedIpAndNoLoopback() {
        val state = vm.ui.value
        assertTrue(state.liveInterfaces.isNotEmpty())
        val eth0 = state.liveInterfaces.firstOrNull { it.name == "eth0" }
        assertNotNull(eth0)
        assertTrue(eth0!!.addresses.any { it.ip == "192.168.1.100" })
        assertFalse(state.liveInterfaces.any { it.name == "lo" })
        assertFalse(state.liveInterfaces.flatMap { it.addresses }.any { it.ip == "127.0.0.1" })
    }

    // -------------------------------------------------------------------------
    // 8. OPS Board Phase 2 Pinning and Layout
    // -------------------------------------------------------------------------

    @Test
    fun pinSnapshots_movesSnapshotsBeforeNetworkInAppliedKeys() {
        vm.pinSection(GuestSection.SNAPSHOTS)
        val keys = vm.ui.value.appliedKeys
        val snapIdx = keys.indexOf("snapshots")
        val netIdx = keys.indexOf("network")
        assertTrue("snapshots should be present", snapIdx >= 0)
        assertTrue("network should be present", netIdx >= 0)
        assertTrue("snapshots ($snapIdx) must appear before network ($netIdx)", snapIdx < netIdx)
    }

    @Test
    fun toggleHistoryFalse_omitsHistoryGraphFromAppliedKeys() {
        vm.toggleHistory(false)
        val keys = vm.ui.value.appliedKeys
        assertFalse(keys.contains("history-graph"))
        assertTrue(keys.contains("console"))
    }

    @Test
    fun togglePinSectionRaw_doesNotPinRaw() {
        vm.togglePinSection(GuestSection.RAW)
        assertFalse(vm.ui.value.board.isPinned(GuestSection.RAW))
        assertFalse(vm.ui.value.board.pinnedSections.contains("RAW"))
        val keys = vm.ui.value.appliedKeys
        assertEquals("raw", keys.last())
    }

    @Test
    fun pinFirstSection_setsExpectedPinHintMessage() {
        vm.pinSection(GuestSection.SNAPSHOTS)
        assertEquals("PINNED TO OPS · EDIT REORDERS THE DECK", vm.ui.value.message)
    }

    @Test
    fun pinCapExceeded_setsPinCap4Message() {
        vm.pinSection(GuestSection.SNAPSHOTS)
        vm.pinSection(GuestSection.HARDWARE)
        vm.pinSection(GuestSection.NETWORK)
        vm.pinSection(GuestSection.FIREWALL)
        assertEquals(4, vm.ui.value.board.pinnedSections.size)

        vm.pinSection(GuestSection.BACKUPS)
        assertEquals("PIN CAP 4", vm.ui.value.message)
    }

    @Test
    fun targetedConsumeMessage_clearsExpectedStringAndPreservesNewMessage() {
        vm.pinSection(GuestSection.SNAPSHOTS)
        vm.pinSection(GuestSection.HARDWARE)
        vm.pinSection(GuestSection.NETWORK)
        vm.pinSection(GuestSection.FIREWALL)
        assertEquals(4, vm.ui.value.board.pinnedSections.size)

        vm.pinSection(GuestSection.BACKUPS)
        assertEquals("PIN CAP 4", vm.ui.value.message)

        vm.consumeMessage("PIN CAP 4")
        assertNull(vm.ui.value.message)

        vm.pinSection(GuestSection.BACKUPS)
        assertEquals("PIN CAP 4", vm.ui.value.message)

        vm.saveBoardAsProfileDefault()
        assertEquals("Board saved as default for all guests", vm.ui.value.message)

        vm.consumeMessage("PIN CAP 4")
        assertEquals("Board saved as default for all guests", vm.ui.value.message)

        vm.consumeMessage("Board saved as default for all guests")
        assertNull(vm.ui.value.message)
    }

    @Test
    fun targetedConsumeError_clearsExpectedErrorAndPreservesMismatch() {
        vm.createSnapshot("", "", false)
        assertEquals("Invalid snapshot name", vm.ui.value.error)

        vm.consumeError("Something else")
        assertEquals("Invalid snapshot name", vm.ui.value.error)

        vm.consumeError("Invalid snapshot name")
        assertNull(vm.ui.value.error)
    }
}

