package com.pxmx.app.ui.log

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ClusterLogEntry
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
class LogViewModelTest {

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
    fun logViewModel_initialLoad_fetchesClusterLogsAndNodes() = runBlocking {
        val vm = LogViewModel(repository)
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertEquals("cluster", state.selectedScope)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)

        assertTrue("Should have discovered node names", state.nodeNames.isNotEmpty())
        assertTrue("Cluster logs should not be empty", state.logs.isNotEmpty())

        job.cancel()
    }

    @Test
    fun logViewModel_selectScope_switchesToNodeSyslog() = runBlocking {
        val vm = LogViewModel(repository)
        val job = launch(testDispatcher) { vm.ui.collect() }

        // Switch scope to "alpha"
        vm.selectScope("alpha")
        assertEquals("alpha", vm.ui.value.selectedScope)
        assertFalse(vm.ui.value.loading)
        assertNull(vm.ui.value.error)

        val nodeLogs = vm.ui.value.logs
        assertTrue("Node logs should not be empty", nodeLogs.isNotEmpty())
        assertTrue("All logs should belong to alpha", nodeLogs.all { it.node == "alpha" })

        // Switch back to "cluster"
        vm.selectScope("cluster")
        assertEquals("cluster", vm.ui.value.selectedScope)

        job.cancel()
    }

    @Test
    fun logViewModel_refresh_updatesLogs() = runBlocking {
        val vm = LogViewModel(repository)
        val job = launch(testDispatcher) { vm.ui.collect() }

        vm.refresh()
        assertFalse(vm.ui.value.loading)
        assertFalse(vm.ui.value.refreshing)
        assertTrue(vm.ui.value.logs.isNotEmpty())

        job.cancel()
    }

    @Test
    fun logViewModel_errorHandling_recordsError() = runBlocking {
        val failingApi = object : ProxmoxApi by demoApi {
            override suspend fun clusterLog(max: Int, since: Long?): PveResponse<List<ClusterLogEntry>> {
                throw PveException("Syslog daemon offline")
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

        val vm = LogViewModel(customRepo)
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Syslog daemon offline"))

        job.cancel()
    }

    @Test
    fun logViewModel_factory_createsInstance() {
        val factory = LogViewModel.Factory(repository)
        val vm = factory.create(LogViewModel::class.java)
        assertNotNull(vm)
    }
}
