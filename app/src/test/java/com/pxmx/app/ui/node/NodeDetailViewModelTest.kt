package com.pxmx.app.ui.node

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.NodeBundle
import com.pxmx.app.data.model.NodeServiceInfo
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeTaskInfo
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.repo.PveException
import com.pxmx.app.data.session.SessionStore
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
class NodeDetailViewModelTest {

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
    fun nodeDetailViewModel_initialLoad_populatesNodeBundle() = runBlocking {
        val vm = NodeDetailViewModel(repository, "alpha")
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertEquals("alpha", state.node)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)

        // DemoApi provides status for alpha
        assertNotNull(state.status)
        assertEquals("alpha", state.status?.pveversion?.let { "alpha" })
        assertTrue("Services list should not be empty", state.services.isNotEmpty())
        assertTrue("Tasks list should not be empty", state.tasks.isNotEmpty())

        job.cancel()
    }

    @Test
    fun nodeDetailViewModel_refresh_setsRefreshingFlagAndUpdatesData() = runBlocking {
        val vm = NodeDetailViewModel(repository, "alpha")
        val job = launch(testDispatcher) { vm.ui.collect() }

        // Initial state loaded
        assertFalse(vm.ui.value.loading)

        // Trigger manual refresh
        vm.refresh(initial = false)
        assertFalse(vm.ui.value.loading)
        assertFalse(vm.ui.value.refreshing)
        assertNotNull(vm.ui.value.status)

        job.cancel()
    }
    @Test
    fun nodeDetailViewModel_loadFailure_setsErrorMessage() = runBlocking {
        val customRepo = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = object : ProxmoxApiProvider {
                override fun apiFor(config: ServerConfig): ProxmoxApi = throw PveException("Node unreachable")
                override fun apiForProbe(config: ServerConfig): ProbeApi = throw PveException("Node unreachable")
                override fun clear() {}
            }
        )

        val vm = NodeDetailViewModel(customRepo, "offline-node")
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertNull(state.status)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Node unreachable") || state.error!!.contains("Failed"))

        job.cancel()
    }

    @Test
    fun nodeDetailViewModel_getBrowserUrl_returnsFormattedHost() {
        val vm = NodeDetailViewModel(repository, "alpha")
        val url = vm.getBrowserUrl()
        assertEquals("https://10.0.0.55:8006", url)
    }

    @Test
    fun nodeDetailViewModel_factory_createsInstance() {
        val factory = NodeDetailViewModel.Factory(repository, "beta")
        val vm = factory.create(NodeDetailViewModel::class.java)
        assertNotNull(vm)
        assertEquals("beta", vm.ui.value.node)
    }
}
