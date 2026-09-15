package com.pxmx.app.data.repo

import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.*
import com.pxmx.app.data.model.*
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PartialAvailabilityTest {
    private val demo = DemoApi()
    private val store = SessionStore(injectedPrefs = FakeSharedPreferences()).apply {
        setSession(SessionState(ServerConfig(host = "entry.example"), ticket = "test-ticket", username = "root@pam"))
    }
    private fun repository(api: ProxmoxApi) = ProxmoxRepository(ContextWrapper(null), store,
        object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig) = api
            override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
            override fun clear() {}
        })

    private fun intercept(before: (String, Array<out Any?>) -> Unit): ProxmoxApi =
        java.lang.reflect.Proxy.newProxyInstance(ProxmoxApi::class.java.classLoader, arrayOf(ProxmoxApi::class.java)) { _, method, args ->
            before(method.name, args ?: emptyArray())
            try { method.invoke(demo, *(args ?: emptyArray())) }
            catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
        } as ProxmoxApi

    @Test fun successfulAptRefreshWithFailedVerificationMarksOldZeroUnknown() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            var verificationFails = false
            var taskChecks = 0
            val api = object : ProxmoxApi by demo {
                override suspend fun aptUpdateList(node: String): PveResponse<List<Map<String, Any>>> {
                    if (verificationFails) throw IOException("verification unavailable")
                    return PveResponse(data = emptyList())
                }
                override suspend fun aptUpdateRefresh(node: String) = PveResponse(data = "UPID:alpha:aptupdate")
                override suspend fun taskStatus(node: String, upid: String): PveResponse<TaskStatus> {
                    taskChecks++
                    verificationFails = true
                    return PveResponse(data = TaskStatus(status = "stopped", exitstatus = "OK"))
                }
            }
            val vm = com.pxmx.app.ui.settings.UpdatesViewModel(repository(api))
            models.put("updates", vm)
            runCurrent()
            val previous = vm.ui.value.nodes
            assertTrue(previous.isNotEmpty())
            assertEquals(0, vm.ui.value.totalPending)
            vm.refreshAptDb("alpha")
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(1, taskChecks)
            assertEquals(previous, vm.ui.value.nodes)
            assertNotNull(vm.ui.value.error)
            assertEquals(com.pxmx.app.ui.settings.NodeRefreshState.ERROR, vm.ui.value.progress["alpha"]?.state)
            assertTrue(vm.ui.value.progress["alpha"]?.readFailed == true)
            assertTrue(vm.ui.value.progress["alpha"]?.errorDetail.orEmpty().contains("unknown", ignoreCase = true))
            vm.dismissProgress("alpha")
            assertTrue("Dismissing cannot turn unverified zero into CURRENT", vm.ui.value.progress["alpha"]?.readFailed == true)
            verificationFails = false
            vm.refresh()
            runCurrent()
            assertNull(vm.ui.value.error)
            assertFalse(vm.ui.value.progress["alpha"]?.readFailed == true)
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test fun coldGuestKeepsCoreWhenAuxiliariesFailAndLxcNeverRequestsUsb() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            for (type in listOf(GuestType.QEMU, GuestType.LXC)) {
                var usbCalls = 0
                val api = intercept { method, _ ->
                    if (method == "nodeUsb") usbCalls++
                    if (method in setOf("nodeUsb", "nodeStorage", "guestSnapshots")) throw IOException("$method unavailable")
                }
                val vm = com.pxmx.app.ui.guest.GuestDetailViewModel(repository(api), "alpha", type, 100, "guest")
                models.put(type.path, vm)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
                runCurrent()
                val state = vm.ui.value
                assertNotNull("Core status must survive optional reads", state.status)
                assertNotNull("Core configuration must survive optional reads", state.config)
                assertTrue(state.error.orEmpty().contains("snapshots", ignoreCase = true))
                assertTrue(state.error.orEmpty().contains("backup", ignoreCase = true))
                assertEquals(if (type == GuestType.QEMU) 1 else 0, usbCalls)
                if (type == GuestType.QEMU) assertTrue(state.error.orEmpty().contains("USB", ignoreCase = true))
            }
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test fun guestRefreshRetainsOnlyFailedAuxiliarySources() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            var fail = false
            val api = object : ProxmoxApi by demo {
                override suspend fun guestStatus(node: String, type: String, vmid: Long) =
                    PveResponse(data = GuestStatus(name = if (fail) "fresh" else "old", status = "running"))
                override suspend fun guestConfig(node: String, type: String, vmid: Long, current: Int?) =
                    PveResponse<Map<String, Any>>(data = mapOf("name" to if (fail) "fresh" else "old", "memory" to if (fail) 2048 else 1024))
                override suspend fun nodeUsb(node: String): PveResponse<List<HostUsbDevice>> {
                    if (fail) throw IOException("USB offline")
                    return PveResponse(data = listOf(HostUsbDevice(vendid = "1234", prodid = "5678")))
                }
                override suspend fun guestSnapshots(node: String, type: String, vmid: Long) =
                    PveResponse(data = if (fail) emptyList() else listOf(SnapshotInfo(name = "old")))
                override suspend fun nodeStorage(node: String) = PveResponse(data = listOf(
                    NodeStorageEntry(storage = "healthy", content = "backup"), NodeStorageEntry(storage = "offline", content = "backup")))
                override suspend fun storageContent(node: String, storage: String, content: String?, vmid: Long?): PveResponse<List<StorageContentItem>> {
                    if (fail && storage == "offline") throw IOException("backup storage offline")
                    return PveResponse(data = listOf(StorageContentItem(volid = "$storage:backup/${if (fail) "fresh" else "old"}", vmid = vmid)))
                }
            }
            val vm = com.pxmx.app.ui.guest.GuestDetailViewModel(repository(api), "alpha", GuestType.QEMU, 100, "guest")
            models.put("guest", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
            runCurrent()
            val previous = vm.ui.value
            assertEquals(2, previous.backups.size)
            assertTrue(previous.hostUsbs.isNotEmpty())
            fail = true
            vm.refresh()
            runCurrent()
            val state = vm.ui.value
            assertEquals("fresh", state.status?.name)
            assertEquals("fresh", state.config?.name)
            assertEquals(previous.hostUsbs, state.hostUsbs)
            assertTrue(state.snapshots.isEmpty())
            assertEquals(setOf("healthy:backup/fresh", "offline:backup/old"), state.backups.map { it.volid }.toSet())
            assertTrue(state.error.orEmpty().contains("backups/offline"))
            assertFalse(state.error.orEmpty().contains("backups/healthy"))
            fail = false
            vm.refresh()
            runCurrent()
            assertNull(vm.ui.value.error)
            assertEquals(previous.backups, vm.ui.value.backups)
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test fun homeRefreshRetainsOnlyFailedSectionsAndClearsThemOnRecovery() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            var fail = false
            val api = object : ProxmoxApi by demo {
                override suspend fun nodeQemu(node: String): PveResponse<List<ClusterResource>> {
                    if (fail && node == "beta") throw IOException("beta qemu offline")
                    val rows = demo.nodeQemu(node).data.orEmpty()
                    return PveResponse(data = if (fail) rows.map { it.copy(name = "fresh-${it.vmid}") } else rows)
                }
                override suspend fun nodeLxc(node: String): PveResponse<List<ClusterResource>> =
                    if (fail) PveResponse(data = emptyList()) else demo.nodeLxc(node)
            }
            val vm = HomeViewModel(repository(api), store)
            models.put("home", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
            runCurrent()
            val previous = vm.ui.value.resources.filter { it.node == "beta" && it.type == "qemu" }
            assertTrue(previous.isNotEmpty())
            assertTrue(vm.ui.value.resources.any { it.type == "lxc" })
            fail = true
            vm.refresh()
            runCurrent()
            assertEquals(previous.map { it.id }, vm.ui.value.resources.filter { it.node == "beta" && it.type == "qemu" }.map { it.id })
            assertTrue(vm.ui.value.resources.filter { it.node == "alpha" && it.type == "qemu" }.all { it.name.orEmpty().startsWith("fresh-") })
            assertFalse("Successful empty sections must clear stale entries", vm.ui.value.resources.any { it.type == "lxc" })
            assertTrue(vm.ui.value.error.orEmpty().contains("beta/qemu"))
            fail = false
            vm.refresh()
            runCurrent()
            assertNull(vm.ui.value.error)
            assertEquals(previous, vm.ui.value.resources.filter { it.node == "beta" && it.type == "qemu" })
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test fun coldHomeKeepsHealthyGuestsWhenAnotherNodeIsOffline() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val api = intercept { method, args ->
                if (args.firstOrNull() == "beta" && method in setOf("nodeStatus", "nodeQemu", "nodeLxc", "nodeStorage")) {
                    throw IOException("beta offline")
                }
            }
            val vm = HomeViewModel(repository(api), store)
            models.put("home", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
            runCurrent()
            val state = vm.ui.value
            assertTrue("Healthy guests must be accessible on cold start", state.resources.any { it.node == "alpha" && it.isGuest })
            assertTrue(state.resources.any { it.node == "alpha" && it.type == "node" })
            assertEquals("unknown", state.resources.single { it.node == "beta" && it.type == "node" }.status)
            assertTrue(state.error.orEmpty().contains("beta"))
            assertTrue(state.error.orEmpty().contains("qemu"))
            assertTrue(state.error.orEmpty().contains("storage"))
        } finally { models.clear(); Dispatchers.resetMain() }
    }
}
