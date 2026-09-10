package com.pxmx.app.ui.home

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProxmoxRepository
    private lateinit var demoApi: DemoApi

    private val sampleResources = listOf(
        ClusterResource(
            id = "node/pve1",
            type = "node",
            node = "pve1",
            status = "online",
            cpu = 0.15,
            maxcpu = 8,
            mem = 4_000_000_000L,
            maxmem = 32_000_000_000L,
        ),
        ClusterResource(
            id = "qemu/100",
            type = "qemu",
            node = "pve1",
            vmid = 100L,
            name = "web01",
            status = "running",
            cpu = 0.40,
            maxcpu = 4,
            mem = 2_000_000_000L,
            maxmem = 8_000_000_000L,
            disk = 10_000_000_000L,
            maxdisk = 50_000_000_000L,
            onboot = 1,
            tags = "prod,web",
        ),
        ClusterResource(
            id = "qemu/101",
            type = "qemu",
            node = "pve1",
            vmid = 101L,
            name = "db01",
            status = "stopped",
            onboot = 0,
            tags = "dev",
        ),
        ClusterResource(
            id = "lxc/200",
            type = "lxc",
            node = "pve1",
            vmid = 200L,
            name = "dns01",
            status = "running",
            cpu = 0.05,
            maxcpu = 2,
            mem = 500_000_000L,
            maxmem = 1_000_000_000L,
        ),
        ClusterResource(
            id = "storage/pve1/local-zfs",
            type = "storage",
            node = "pve1",
            storage = "local-zfs",
            disk = 50_000_000_000L,
            maxdisk = 200_000_000_000L,
            status = "available",
        ),
    )

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 1. HomeUiState Filtering Tests
    // -------------------------------------------------------------------------

    @Test
    fun filtered_filterGuests_returnsOnlyQemuAndLxc() {
        val state = HomeUiState(resources = sampleResources, filter = ResourceFilter.GUESTS)
        val guests = state.filtered
        assertEquals(3, guests.size)
        assertTrue(guests.all { it.type == "qemu" || it.type == "lxc" })
    }

    @Test
    fun filtered_filterNodes_returnsOnlyNodes() {
        val state = HomeUiState(resources = sampleResources, filter = ResourceFilter.NODES)
        val nodes = state.filtered
        assertEquals(1, nodes.size)
        assertEquals("pve1", nodes.first().node)
    }

    @Test
    fun filtered_filterStorage_returnsOnlyStorage() {
        val state = HomeUiState(resources = sampleResources, filter = ResourceFilter.STORAGE)
        val storages = state.filtered
        assertEquals(1, storages.size)
        assertEquals("local-zfs", storages.first().storage)
    }

    @Test
    fun filtered_filterAll_returnsAllResources() {
        val state = HomeUiState(resources = sampleResources, filter = ResourceFilter.ALL)
        assertEquals(5, state.filtered.size)
    }

    // -------------------------------------------------------------------------
    // 2. Search Query Matching Tests
    // -------------------------------------------------------------------------

    @Test
    fun filtered_searchQuery_matchesNameNodeVmidAndTags() {
        val baseState = HomeUiState(resources = sampleResources, filter = ResourceFilter.ALL)

        // Search by name
        val nameSearch = baseState.copy(searchQuery = "web01").filtered
        assertEquals(1, nameSearch.size)
        assertEquals("web01", nameSearch.first().name)

        // Search by VMID
        val vmidSearch = baseState.copy(searchQuery = "101").filtered
        assertEquals(1, vmidSearch.size)
        assertEquals("db01", vmidSearch.first().name)

        // Search by tag
        val tagSearch = baseState.copy(searchQuery = "prod").filtered
        assertEquals(1, tagSearch.size)
        assertEquals("web01", tagSearch.first().name)

        // Search by node
        val nodeSearch = baseState.copy(searchQuery = "pve1").filtered
        assertEquals(5, nodeSearch.size)

        // Non-matching search
        val emptySearch = baseState.copy(searchQuery = "nonexistent").filtered
        assertTrue(emptySearch.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 3. Sorting & Grouping Tests
    // -------------------------------------------------------------------------

    @Test
    fun filtered_sortGuestsByName_sortsAlphabetically() {
        val state = HomeUiState(
            resources = sampleResources,
            filter = ResourceFilter.GUESTS,
            sortGuests = ResourceSort.NAME,
        )
        val names = state.filtered.map { it.name }
        assertEquals(listOf("db01", "dns01", "web01"), names)
    }

    @Test
    fun filtered_sortGuestsByUsage_sortsHighestFirst() {
        val state = HomeUiState(
            resources = sampleResources,
            filter = ResourceFilter.GUESTS,
            sortGuests = ResourceSort.USAGE,
        )
        val names = state.filtered.map { it.name }
        assertEquals("web01", names.first())
        assertEquals("db01", names.last())
    }

    @Test
    fun listRows_defaultSort_generatesStatusSections() {
        val state = HomeUiState(
            resources = sampleResources,
            filter = ResourceFilter.GUESTS,
            sortGuests = ResourceSort.DEFAULT,
        )
        val rows = state.listRows
        val sections = rows.filterIsInstance<HomeListRow.Section>()
        assertEquals(2, sections.size)
        assertTrue(sections.any { it.title.contains("RUNNING", ignoreCase = true) && it.count == 2 })
        assertTrue(sections.any { it.title.contains("STOPPED", ignoreCase = true) && it.count == 1 })
    }

    @Test
    fun listRows_metricSort_returnsFlatItemsWithoutSections() {
        val state = HomeUiState(
            resources = sampleResources,
            filter = ResourceFilter.GUESTS,
            sortGuests = ResourceSort.USAGE,
        )
        val rows = state.listRows
        assertTrue(rows.all { it is HomeListRow.Item })
        assertEquals(3, rows.size)
    }

    // -------------------------------------------------------------------------
    // 4. ViewModel Dialog Toggles & State Mutation
    // -------------------------------------------------------------------------

    @Test
    fun homeViewModel_dialogToggles_updateUiState() = runBlocking {
        val vm = HomeViewModel(repository, sessionStore)
        val collectJob = launch(UnconfinedTestDispatcher()) { vm.ui.collect() }

        vm.showDeployDialog(true)
        assertTrue(vm.ui.value.showDeployDialog)
        vm.showDeployDialog(false)
        assertFalse(vm.ui.value.showDeployDialog)

        vm.showAccounts(true)
        assertTrue(vm.ui.value.showAccounts)
        vm.showAccounts(false)
        assertFalse(vm.ui.value.showAccounts)

        vm.showThemePicker(true)
        assertTrue(vm.ui.value.showThemePicker)
        vm.showThemePicker(false)
        assertFalse(vm.ui.value.showThemePicker)

        collectJob.cancel()
    }

    @Test
    fun homeViewModel_filterAndSort_persistsInUiState() = runBlocking {
        val vm = HomeViewModel(repository, sessionStore)
        val collectJob = launch(UnconfinedTestDispatcher()) { vm.ui.collect() }

        vm.setFilter(ResourceFilter.STORAGE)
        assertEquals(ResourceFilter.STORAGE, vm.ui.value.filter)

        vm.setSort(ResourceSort.CAPACITY)
        assertEquals(ResourceSort.CAPACITY, vm.ui.value.sortStorage)

        vm.setSearchQuery("test-query")
        assertEquals("test-query", vm.ui.value.searchQuery)

        collectJob.cancel()
    }

    @Test
    fun homeViewModel_quickPowerToggle_and_action_dispatches() = runBlocking {
        val vm = HomeViewModel(repository, sessionStore)

        val runningGuest = sampleResources.first { it.id == "qemu/100" }
        val stoppedGuest = sampleResources.first { it.id == "qemu/101" }

        // Running guest power toggle invokes shutdown
        val job1 = vm.quickPowerToggle(runningGuest, false)
        assertNotNull(job1)

        // Stopped guest power toggle invokes start
        val job2 = vm.quickPowerToggle(stoppedGuest, true)
        assertNotNull(job2)

        // Action trigger dispatches
        val job3 = vm.triggerGuestAction(runningGuest, GuestAction.RESET)
        assertNotNull(job3)
    }

    @Test
    fun homeViewModel_quickPowerToggle_whenGuestBusy_debouncesSecondaryInvocation() = runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
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
        val testVm = HomeViewModel(customRepo, sessionStore)
        val job = launch(UnconfinedTestDispatcher()) { testVm.ui.collect() }
        val guest = sampleResources.first { it.id == "qemu/100" }

        // First toggle immediately marks guest busy and suspends waiting for gate
        testVm.quickPowerToggle(guest, false)
        assertTrue(guest.id in testVm.ui.value.busyGuestIds)

        // Secondary toggle while busy is rejected/debounced
        testVm.quickPowerToggle(guest, true)
        val currentGuest = testVm.ui.value.resources.first { it.id == "qemu/100" }
        assertEquals("stopped", currentGuest.status)

        // Release gate and clean up
        gate.complete(Unit)
        job.cancel()
    }
}
