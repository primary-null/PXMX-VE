package com.pxmx.app.ui.settings

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
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
class NetworkViewModelTest {

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
    fun networkViewModel_initialLoad_populatesNodeNetworks() = runBlocking {
        val vm = NetworkViewModel(repository)
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)

        assertTrue("Should return network snapshots for cluster nodes", state.nodes.isNotEmpty())
        val firstNodeNet = state.nodes.first()
        assertTrue("Node should have network interfaces", firstNodeNet.interfaces.isNotEmpty())
        assertTrue("Node should contain vmbr0 bridge", firstNodeNet.interfaces.any { it.iface == "vmbr0" })

        job.cancel()
    }

    @Test
    fun networkViewModel_refresh_reloadsClusterNetwork() = runBlocking {
        val vm = NetworkViewModel(repository)
        val job = launch(testDispatcher) { vm.ui.collect() }

        vm.refresh()
        assertFalse(vm.ui.value.loading)
        assertFalse(vm.ui.value.refreshing)
        assertTrue(vm.ui.value.nodes.isNotEmpty())

        job.cancel()
    }

    @Test
    fun networkViewModel_failure_setsError() = runBlocking {
        val failingApi = object : ProxmoxApi by demoApi {
            override suspend fun nodes(): com.pxmx.app.data.model.PveResponse<List<com.pxmx.app.data.model.ClusterResource>> {
                throw PveException("Networking service unavailable")
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

        val vm = NetworkViewModel(customRepo)
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Networking service unavailable"))

        job.cancel()
    }

    @Test
    fun networkViewModel_factory_createsInstance() {
        val factory = NetworkViewModel.Factory(repository)
        val vm = factory.create(NetworkViewModel::class.java)
        assertNotNull(vm)
    }
}
