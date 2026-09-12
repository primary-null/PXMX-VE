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
import com.pxmx.app.data.model.TaskStatus
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
class SdnViewModelTest {

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
    fun sdnViewModel_initialLoad_populatesZonesVnetsStatuses() = runBlocking {
        val vm = SdnViewModel(repository)
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)

        assertTrue("Zones should be populated", state.zones.isNotEmpty())
        assertTrue("Vnets should be populated", state.vnets.isNotEmpty())
        assertTrue("Statuses should be populated", state.statuses.isNotEmpty())
        assertTrue("Healthy demo cluster should have empty issueStatuses", state.issueStatuses.isEmpty())

        job.cancel()
    }

    @Test
    fun sdnViewModel_applySdn_executesTaskFlowAndRefreshes() = runBlocking {
        val instantTaskApi = object : ProxmoxApi by demoApi {
            override suspend fun applySdn(): PveResponse<String?> = PveResponse(data = "OK")
        }
        val customRepo = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = object : ProxmoxApiProvider {
                override fun apiFor(config: ServerConfig): ProxmoxApi = instantTaskApi
                override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(instantTaskApi)
                override fun clear() {}
            }
        )
        val vm = SdnViewModel(customRepo)
        val job = launch(testDispatcher) { vm.ui.collect() }

        // Trigger applySdn and await completion
        val applyJob = vm.applySdn()
        applyJob?.join()

        // After completion, isApplying is false and no error
        assertFalse(vm.ui.value.isApplying)
        assertNull(vm.ui.value.actionError)
        assertNull(vm.ui.value.jobStatus)
        assertTrue(vm.ui.value.zones.isNotEmpty())

        job.cancel()
    }

    @Test
    fun sdnViewModel_applySdn_failure_setsActionError() = runBlocking {
        val failingApi = object : ProxmoxApi by demoApi {
            override suspend fun applySdn(): PveResponse<String?> {
                throw PveException("SDN lock busy")
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

        val vm = SdnViewModel(customRepo)
        val job = launch(testDispatcher) { vm.ui.collect() }

        vm.applySdn()
        assertFalse(vm.ui.value.isApplying)
        assertNotNull(vm.ui.value.actionError)
        assertTrue(vm.ui.value.actionError!!.contains("SDN lock busy"))

        // Test clearActionError
        vm.clearActionError()
        assertNull(vm.ui.value.actionError)

        job.cancel()
    }

    @Test
    fun sdnViewModel_factory_createsInstance() {
        val factory = SdnViewModel.Factory(repository)
        val vm = factory.create(SdnViewModel::class.java)
        assertNotNull(vm)
    }
}
