package com.pxmx.app.ui.console

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ConsoleProxyData
import com.pxmx.app.data.model.GuestType
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
class ConsoleViewModelTest {

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
                config = ServerConfig(
                    host = "10.0.0.55",
                    port = 8006,
                    trustSelfSigned = true,
                ),
                ticket = "PVE:root@pam:ticket1234",
                csrf = "csrf1234",
            )
        )
        sessionStore.saveCertPin("10.0.0.55", "SHA256:PIN1234567890")

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
    fun consoleViewModel_openQemuConsole_success() = runBlocking {
        val vm = ConsoleViewModel(
            repository = repository,
            sessionStore = sessionStore,
            node = "alpha",
            guestType = GuestType.QEMU,
            vmid = 100L,
            name = "web01",
        )
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertTrue(state.trustSelfSigned)
        assertEquals("SHA256:PIN1234567890", state.certPin)

        val session = state.session
        assertNotNull(session)
        assertEquals("web01", session?.name)
        assertEquals("alpha", session?.node)
        assertEquals(100L, session?.vmid)
        assertEquals(GuestType.QEMU, session?.guestType)
        assertTrue("URL should be noVNC path", session?.pageUrl?.contains("novnc") == true)

        job.cancel()
    }

    @Test
    fun consoleViewModel_openLxcConsole_success() = runBlocking {
        val vm = ConsoleViewModel(
            repository = repository,
            sessionStore = sessionStore,
            node = "alpha",
            guestType = GuestType.LXC,
            vmid = 200L,
            name = "dns01",
        )
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertNull(state.error)

        val session = state.session
        assertNotNull(session)
        assertEquals(GuestType.LXC, session?.guestType)

        job.cancel()
    }

    @Test
    fun consoleViewModel_openNodeShell_success() = runBlocking {
        val vm = ConsoleViewModel(
            repository = repository,
            sessionStore = sessionStore,
            node = "alpha",
            guestType = GuestType.NODE,
            vmid = 0L,
            name = "alpha",
            cmd = "shell",
        )
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertNull(state.error)

        val session = state.session
        assertNotNull(session)
        assertEquals(GuestType.NODE, session?.guestType)
        assertTrue("Node shell URL should point to xtermjs", session?.pageUrl?.contains("xtermjs") == true)

        job.cancel()
    }

    @Test
    fun consoleViewModel_openFailure_setsErrorState() = runBlocking {
        val failingApi = object : ProxmoxApi by demoApi {
            override suspend fun qemuVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> {
                throw PveException("VM 999 not running")
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

        val vm = ConsoleViewModel(
            repository = customRepo,
            sessionStore = sessionStore,
            node = "alpha",
            guestType = GuestType.QEMU,
            vmid = 999L,
            name = "bad-vm",
        )
        val job = launch(testDispatcher) { vm.ui.collect() }

        val state = vm.ui.value
        assertFalse(state.loading)
        assertNull(state.session)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("VM 999 not running"))

        job.cancel()
    }

    @Test
    fun consoleViewModel_factory_createsInstance() {
        val factory = ConsoleViewModel.Factory(
            repository = repository,
            sessionStore = sessionStore,
            node = "alpha",
            guestType = GuestType.NODE,
            vmid = 0L,
            name = "alpha",
        )
        val vm = factory.create(ConsoleViewModel::class.java)
        assertNotNull(vm)
    }
}
