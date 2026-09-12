package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProxmoxRepositoryTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var demoApi: DemoApi
    private lateinit var repository: ProxmoxRepository

    @Before
    fun setup() {
        demoApi = DemoApi()
        val fakePrefs = FakeSharedPreferences()
        sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "10.0.0.55", port = 8006),
                ticket = "PVE:root@pam:ticket1234",
                csrf = "csrf1234",
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

    // -------------------------------------------------------------------------
    // 1. Resource Discovery & Aggregation
    // -------------------------------------------------------------------------

    @Test
    fun listResources_all_aggregatesNodesGuestsAndStorage() = runBlocking {
        val result = repository.listResources()
        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertTrue("Resource list must not be empty", list.isNotEmpty())

        assertTrue("Should contain nodes", list.any { it.type == "node" })
        assertTrue("Should contain qemu guests", list.any { it.type == "qemu" })
        assertTrue("Should contain lxc guests", list.any { it.type == "lxc" })
        assertTrue("Should contain storage pools", list.any { it.type == "storage" })
    }

    @Test
    fun listResources_filterVm_returnsOnlyQemuAndLxc() = runBlocking {
        val result = repository.listResources(type = "vm")
        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertTrue(list.isNotEmpty())
        assertTrue("All resources must be qemu or lxc", list.all { it.type == "qemu" || it.type == "lxc" })
    }

    @Test
    fun listResources_filterStorage_returnsOnlyStorage() = runBlocking {
        val result = repository.listResources(type = "storage")
        assertTrue(result.isSuccess)
        val list = result.getOrNull().orEmpty()
        assertTrue(list.isNotEmpty())
        assertTrue("All resources must be storage", list.all { it.type == "storage" })
    }

    // -------------------------------------------------------------------------
    // 2. Site Info & Cluster Detection
    // -------------------------------------------------------------------------

    @Test
    fun siteInfo_detectsMultiNodeCluster() = runBlocking {
        val result = repository.siteInfo()
        assertTrue(result.isSuccess)
        val info = result.getOrNull()
        assertNotNull(info)
        assertTrue("3-node demo cluster should have isCluster == true", info!!.isCluster)
        assertTrue("Node count should be at least 3", info.nodeCount >= 3)
    }

    // -------------------------------------------------------------------------
    // 3. Guest Actions
    // -------------------------------------------------------------------------

    @Test
    fun guestAction_qemu_dispatchesExpectedActions() = runBlocking {
        val startRes = repository.guestAction("alpha", GuestType.QEMU, 100L, GuestAction.START)
        assertTrue(startRes.isSuccess)
        assertTrue(startRes.getOrNull()?.startsWith("UPID:alpha:") == true)

        val rebootRes = repository.guestAction("alpha", GuestType.QEMU, 100L, GuestAction.REBOOT)
        assertTrue(rebootRes.isSuccess)

        val shutdownRes = repository.guestAction("alpha", GuestType.QEMU, 100L, GuestAction.SHUTDOWN)
        assertTrue(shutdownRes.isSuccess)

        val stopRes = repository.guestAction("alpha", GuestType.QEMU, 100L, GuestAction.STOP)
        assertTrue(stopRes.isSuccess)
    }

    @Test
    fun guestAction_lxc_dispatchesExpectedActions() = runBlocking {
        val startRes = repository.guestAction("alpha", GuestType.LXC, 200L, GuestAction.START)
        assertTrue(startRes.isSuccess)
        assertTrue(startRes.getOrNull()?.startsWith("UPID:alpha:") == true)

        val stopRes = repository.guestAction("alpha", GuestType.LXC, 200L, GuestAction.STOP)
        assertTrue(stopRes.isSuccess)
    }

    // -------------------------------------------------------------------------
    // 4. Node Bundle & Status
    // -------------------------------------------------------------------------

    @Test
    fun loadNodeBundle_loadsStatusServicesAndTasksConcurrently() = runBlocking {
        val result = repository.loadNodeBundle("alpha")
        assertTrue(result.isSuccess)
        val bundle = result.getOrNull()
        assertNotNull(bundle)
        assertNotNull(bundle?.status)
        assertTrue(bundle?.services?.isNotEmpty() == true)
        assertTrue(bundle?.tasks?.isNotEmpty() == true)
    }

    // -------------------------------------------------------------------------
    // 5. Storage Operations
    // -------------------------------------------------------------------------

    @Test
    fun storageDetail_loadsStatusAndContent() = runBlocking {
        val result = repository.storageDetail("alpha", "local")
        assertTrue(result.isSuccess)
        val detail = result.getOrNull()
        assertNotNull(detail)
        assertEquals("alpha", detail?.node)
        assertEquals("local", detail?.storage)
        assertNotNull(detail?.status)
        assertTrue(detail?.content?.isNotEmpty() == true)
    }

    @Test
    fun deleteStorageVolume_invokesApi() = runBlocking {
        val result = repository.deleteStorageVolume("alpha", "local-zfs:backup/vzdump-100.vma.zst")
        assertTrue(result.isSuccess)
    }

    // -------------------------------------------------------------------------
    // 6. Logs & Syslog
    // -------------------------------------------------------------------------

    @Test
    fun logHistory_returnsClusterEvents() = runBlocking {
        val result = repository.logHistory(max = 25)
        assertTrue(result.isSuccess)
        val entries = result.getOrNull().orEmpty()
        assertTrue(entries.isNotEmpty())
    }

    @Test
    fun nodeSyslog_returnsNodeSpecificLines() = runBlocking {
        val result = repository.nodeSyslog("alpha", start = 0, limit = 20)
        assertTrue(result.isSuccess)
        val entries = result.getOrNull().orEmpty()
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.node == "alpha" })
    }

    // -------------------------------------------------------------------------
    // 7. Auto-Connect & Logout
    // -------------------------------------------------------------------------

    @Test
    fun tryAutoConnect_withDisabledPreference_fails() = runBlocking {
        sessionStore.setAutoConnect(false)
        val result = repository.tryAutoConnect()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Auto-connect disabled") == true)
    }

    @Test
    fun logout_clearsSessionAndCache() {
        repository.logout()
        assertNull(sessionStore.session.value)
    }
}
