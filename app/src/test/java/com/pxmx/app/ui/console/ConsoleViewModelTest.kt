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
import com.pxmx.app.data.repo.ConsoleRepository
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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
                    host = "192.168.1.100",
                    port = 8006,
                    trustSelfSigned = true,
                ),
                ticket = "PVE:root@pam:ticket1234",
                csrf = "csrf1234",
            )
        )
        sessionStore.saveCertPin("192.168.1.100", "SHA256:PIN1234567890")

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
        val decodedUrl = URLDecoder.decode(session?.pageUrl.orEmpty(), StandardCharsets.UTF_8.toString())
        assertTrue("URL must contain vncticket matching ticket", decodedUrl.contains("vncticket=DEMO_VNC_TICKET"))
        assertFalse("URL must never contain proxy password", session?.pageUrl?.contains("DEMO_VNC_PASSWORD") == true)
        assertFalse("Decoded URL must never contain proxy password", decodedUrl.contains("DEMO_VNC_PASSWORD"))

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

    @Test
    fun consoleViewModel_directConsoleRepositoryConstructor_success() = runBlocking {
        val vm = ConsoleViewModel(
            consoleRepo = repository.consoleRepo,
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
        assertNotNull(state.session)
        assertEquals("web01", state.session?.name)

        job.cancel()
    }

    @Test
    fun consoleRepository_openConsole_containsTicketAndNeverPassword() = runBlocking {
        val result = repository.consoleRepo.openConsole(
            node = "alpha",
            guestType = GuestType.QEMU,
            vmid = 100L,
            name = "web01",
        )
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        val decodedUrl = URLDecoder.decode(session.pageUrl, StandardCharsets.UTF_8.toString())
        assertTrue(decodedUrl.contains("vncticket=DEMO_VNC_TICKET"))
        assertFalse(session.pageUrl.contains("DEMO_VNC_PASSWORD"))
        assertFalse(decodedUrl.contains("DEMO_VNC_PASSWORD"))
    }

    @Test
    fun consoleRepository_missingTicketFailsEvenIfPasswordIsPresent() = runBlocking {
        val proxyWithOnlyPassword = object : ProxmoxApi by demoApi {
            override suspend fun qemuVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> {
                return PveResponse(
                    data = ConsoleProxyData(
                        port = "5900",
                        ticket = null,
                        password = "PASSWORD_WITHOUT_TICKET",
                    )
                )
            }
        }
        val customRepo = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = object : ProxmoxApiProvider {
                override fun apiFor(config: ServerConfig): ProxmoxApi = proxyWithOnlyPassword
                override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(proxyWithOnlyPassword)
                override fun clear() {}
            }
        )

        val result = customRepo.consoleRepo.openConsole("alpha", GuestType.QEMU, 100L, "test")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is PveException)
        assertTrue(ex!!.message?.contains("missing ticket") == true)
    }

    @Test
    fun consoleRepository_rfbPassword_prefersPasswordOverTicket() {
        val withBoth = ConsoleProxyData(
            port = "5900",
            ticket = "TICKET_VAL",
            password = "PASSWORD_VAL",
        )
        assertEquals("PASSWORD_VAL", ConsoleRepository.rfbPassword(withBoth))
        assertEquals("PASSWORD_VAL", withBoth.rfbPassword)

        val ticketOnly = ConsoleProxyData(
            port = "5900",
            ticket = "TICKET_VAL",
            password = null,
        )
        assertEquals("TICKET_VAL", ConsoleRepository.rfbPassword(ticketOnly))
        assertEquals("TICKET_VAL", ticketOnly.rfbPassword)
    }

    @Test
    fun consoleRepository_demoConsoleStillOpens() = runBlocking {
        val fakePrefs = FakeSharedPreferences()
        val demoStore = SessionStore(injectedPrefs = fakePrefs)
        demoStore.setSession(
            SessionState(
                config = ServerConfig(host = "demo"),
                ticket = null,
            )
        )
        val demoRepo = ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = demoStore,
            clientFactory = object : ProxmoxApiProvider {
                override fun apiFor(config: ServerConfig): ProxmoxApi = demoApi
                override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demoApi)
                override fun clear() {}
            }
        )

        val result = demoRepo.consoleRepo.openConsole("alpha", GuestType.NODE, 0L, "alpha")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertTrue(session.pageUrl.startsWith("data:text/html"))
        assertEquals("https://demo:8006", session.cookieHostUrl)
        assertFalse(session.pageUrl.contains("DEMO_NODE_PASSWORD"))
    }
}
