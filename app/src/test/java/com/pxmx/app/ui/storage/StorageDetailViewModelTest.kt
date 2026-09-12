package com.pxmx.app.ui.storage

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.data.model.StorageStatus
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.repo.PveException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageDetailViewModelTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProxmoxRepository
    private lateinit var demoApi: DemoApi
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        demoApi = DemoApi()
        val fakePrefs = FakeSharedPreferences()
        sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "10.0.0.55", port = 8006),
                ticket = "test-ticket",
                csrf = "test-csrf",
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

    @Test
    fun storageDetailViewModel_initialLoad_populatesStatusAndContent() = runBlocking {
        val vm = StorageDetailViewModel(repository, "alpha", "local")
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertEquals("alpha", state.node)
        assertEquals("local", state.storage)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)

        assertNotNull(state.status)
        assertEquals("local", state.status?.storage)
        assertTrue("Storage content should not be empty", state.content.isNotEmpty())

        val availableTypes = state.availableTypes
        assertTrue("Available types should contain images or iso or backup", availableTypes.isNotEmpty())

        job.cancel()
    }

    @Test
    fun storageDetailViewModel_filtering_filtersContentClientSide() = runBlocking {
        val vm = StorageDetailViewModel(repository, "alpha", "local")
        val job = launch(testDispatcher) { vm.ui.collect() }

        val initialContent = vm.ui.value.content
        assertTrue(initialContent.isNotEmpty())

        // Filter by ISO or first available type
        val targetType = vm.ui.value.availableTypes.firstOrNull() ?: "backup"
        vm.setFilter(targetType)
        assertEquals(targetType, vm.ui.value.contentFilter)
        assertTrue(vm.ui.value.filtered.all { it.content == targetType })

        // Clear filter
        vm.setFilter(null)
        assertNull(vm.ui.value.contentFilter)
        assertEquals(initialContent.size, vm.ui.value.filtered.size)

        job.cancel()
    }

    @Test
    fun storageDetailViewModel_deleteLifecycle_confirmsAndExecutes() = runBlocking {
        val vm = StorageDetailViewModel(repository, "alpha", "local")
        val job = launch(testDispatcher) { vm.ui.collect() }

        val itemToDelete = vm.ui.value.content.firstOrNull()
        assertNotNull(itemToDelete)

        // Set item to confirm delete
        vm.confirmDelete(itemToDelete)
        assertEquals(itemToDelete, vm.ui.value.confirmDelete)

        // Confirm deletion
        vm.deleteConfirmed()
        assertNull(vm.ui.value.confirmDelete)
        assertFalse(vm.ui.value.busy)
        assertNotNull(vm.ui.value.message)
        assertTrue(vm.ui.value.message!!.contains("Deleted"))

        job.cancel()
    }

    @Test
    fun storageDetailViewModel_deleteFailure_reportsError() = runBlocking {
        val failingApi = object : ProxmoxApi by demoApi {
            override suspend fun deleteStorageContent(node: String, storage: String, volume: String): PveResponse<String> {
                throw PveException("Volume in use by VM 100")
            }
        }
        val customRepo = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = object : ProxmoxApiProvider {
                override fun apiFor(config: ServerConfig): ProxmoxApi = failingApi
                override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(failingApi)
                override fun clear() {}
            }
        )

        val vm = StorageDetailViewModel(customRepo, "alpha", "local-zfs")
        val job = launch(testDispatcher) { vm.ui.collect() }

        val item = StorageContentItem(volid = "local-zfs:vm-100-disk-0", content = "images", size = 1000L)
        vm.confirmDelete(item)
        vm.deleteConfirmed()

        assertFalse(vm.ui.value.busy)
        assertNotNull(vm.ui.value.error)
        assertTrue(vm.ui.value.error!!.contains("Volume in use"))

        job.cancel()
    }

    @Test
    fun storageDetailViewModel_factory_createsInstance() {
        val factory = StorageDetailViewModel.Factory(repository, "beta", "backup-nfs")
        val vm = factory.create(StorageDetailViewModel::class.java)
        assertNotNull(vm)
        assertEquals("beta", vm.ui.value.node)
        assertEquals("backup-nfs", vm.ui.value.storage)
    }
}
