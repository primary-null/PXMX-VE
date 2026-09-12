package com.pxmx.app.ui.servers

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.SavedProfile
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {

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
    fun serversViewModel_withNoProfiles_returnsEmptyList() = runBlocking {
        sessionStore.clearSession()
        val vm = ServersViewModel(repository, sessionStore)
        val job = launch(testDispatcher) { vm.ui.collect() }

        assertTrue(vm.ui.value.servers.isEmpty())
        assertFalse(vm.ui.value.refreshing)

        job.cancel()
    }

    @Test
    fun serversViewModel_probesConfiguredProfiles() = runBlocking {
        // Setup live session and profiles
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "10.0.0.55", port = 8006),
                ticket = "ticket",
                csrf = "csrf",
            )
        )
        val p1 = SavedProfile(
            id = "p1",
            label = "Production Cluster",
            host = "10.0.0.55",
            port = 8006,
            password = "test-password",
            authMode = AuthMode.PASSWORD,
            username = "root",
            realm = "pam",
        )
        val p2 = SavedProfile(
            id = "p2",
            label = "Lab Server",
            host = "192.168.1.100",
            port = 8006,
            password = "",
        )
        sessionStore.upsertProfile(p1)
        sessionStore.upsertProfile(p2)

        val vm = ServersViewModel(repository, sessionStore)
        val job = launch(testDispatcher) { vm.ui.collect() }

        val servers = vm.ui.value.servers
        assertEquals(2, servers.size)

        // p1 had saved credentials and was probed via demoApi
        val card1 = servers.first { it.profileId == "p1" }
        assertFalse(card1.loading)
        assertTrue(card1.online)
        assertNotNull(card1.version)
        assertTrue(card1.running >= 0)
        assertNull(card1.errorText)

        // p2 had no saved credentials
        val card2 = servers.first { it.profileId == "p2" }
        assertFalse(card2.loading)
        assertFalse(card2.online)
        assertFalse(card2.hasSavedSecret)

        job.cancel()
    }

    @Test
    fun serversViewModel_whenActiveIsDemo_filtersToDemoOnly() = runBlocking {
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "demo"),
                ticket = "demo-ticket",
            )
        )
        val demoProfile = SavedProfile(
            id = "demo-profile",
            label = "Demo Cluster",
            host = "demo",
            port = 8006,
        )
        val realProfile = SavedProfile(
            id = "real-profile",
            label = "Home PVE",
            host = "192.168.1.50",
            port = 8006,
            password = "secret",
        )
        sessionStore.upsertProfile(demoProfile)
        sessionStore.upsertProfile(realProfile)

        val vm = ServersViewModel(repository, sessionStore)
        val job = launch(testDispatcher) { vm.ui.collect() }

        val servers = vm.ui.value.servers
        assertEquals(1, servers.size)
        assertEquals("demo-profile", servers.first().profileId)

        job.cancel()
    }

    @Test
    fun serversViewModel_factory_createsInstance() {
        val factory = ServersViewModel.Factory(repository, sessionStore)
        val vm = factory.create(ServersViewModel::class.java)
        assertNotNull(vm)
    }
}
