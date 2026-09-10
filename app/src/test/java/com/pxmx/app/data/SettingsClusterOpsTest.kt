package com.pxmx.app.data

import android.content.ContextWrapper
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.repo.PveHttpException
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.settings.FirewallViewModel
import com.pxmx.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SettingsClusterOpsTest {

    private lateinit var demoApi: DemoApi

    @Before
    fun setup() {
        demoApi = DemoApi()
    }

    private fun createRepository(api: ProxmoxApi): ProxmoxRepository {
        val fakePrefs = FakeSharedPreferences()
        val sessionStore = SessionStore(injectedPrefs = fakePrefs)
        sessionStore.setSession(
            SessionState(
                config = ServerConfig(host = "localhost"),
                ticket = "test-ticket",
                csrf = "test-csrf",
            )
        )

        val apiProvider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = api
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(api)
            override fun clear() {}
        }

        return ProxmoxRepository(
            context = ContextWrapper(null),
            sessionStore = sessionStore,
            clientFactory = apiProvider,
        )
    }

    // -------------------------------------------------------------------------
    // 1. Fake API enable flip + digest
    // -------------------------------------------------------------------------

    @Test
    fun clusterFirewallOptions_enableFlip_and_digestRoundtrip() = runBlocking {
        // Initial state from DemoApi
        val initial = demoApi.clusterFirewallOptions().data
        assertNotNull(initial)
        assertEquals(1, initial?.get("enable"))

        // Disable cluster firewall with custom digest
        val res0 = demoApi.setClusterFirewallOptions(enable = 0, digest = "clus_digest_alpha")
        assertNull(res0.errors)
        val afterDisable = demoApi.clusterFirewallOptions().data
        assertNotNull(afterDisable)
        assertEquals(0, afterDisable?.get("enable"))
        assertEquals("clus_digest_alpha", afterDisable?.get("digest"))

        // Re-enable cluster firewall with updated digest
        val res1 = demoApi.setClusterFirewallOptions(enable = 1, digest = "clus_digest_beta")
        assertNull(res1.errors)
        val afterEnable = demoApi.clusterFirewallOptions().data
        assertNotNull(afterEnable)
        assertEquals(1, afterEnable?.get("enable"))
        assertEquals("clus_digest_beta", afterEnable?.get("digest"))
    }

    @Test
    fun nodeFirewallOptions_enableFlip_and_digestRoundtrip() = runBlocking {
        val node = "beta"
        // Initial node options
        val initial = demoApi.nodeFirewallOptions(node).data
        assertNotNull(initial)
        assertEquals(1, initial?.get("enable"))

        // Disable node firewall with custom digest
        val res0 = demoApi.setNodeFirewallOptions(node, enable = 0, digest = "node_beta_dig_1")
        assertNull(res0.errors)
        val afterDisable = demoApi.nodeFirewallOptions(node).data
        assertNotNull(afterDisable)
        assertEquals(0, afterDisable?.get("enable"))
        assertEquals("node_beta_dig_1", afterDisable?.get("digest"))

        // Re-enable node firewall with new digest
        val res1 = demoApi.setNodeFirewallOptions(node, enable = 1, digest = "node_beta_dig_2")
        assertNull(res1.errors)
        val afterEnable = demoApi.nodeFirewallOptions(node).data
        assertNotNull(afterEnable)
        assertEquals(1, afterEnable?.get("enable"))
        assertEquals("node_beta_dig_2", afterEnable?.get("digest"))
    }

    // -------------------------------------------------------------------------
    // 2. applySdn UPID + awaitTask ok
    // -------------------------------------------------------------------------

    @Test
    fun applySdn_returnsValidUpid_and_taskStatus_and_logs() = runBlocking {
        val response = demoApi.applySdn()
        assertNull(response.errors)
        val upid = response.data
        assertNotNull(upid)
        assertTrue("UPID must start with UPID:, got: $upid", upid!!.startsWith("UPID:"))
        assertTrue("UPID must contain sdnreload action, got: $upid", upid.contains("sdnreload"))

        // Check task status endpoint works for sdnreload UPID
        val statusRes = demoApi.taskStatus("alpha", upid)
        assertNull(statusRes.errors)
        val status = statusRes.data
        assertNotNull(status)

        // Check task log stream endpoint returns lines for sdnreload UPID
        val logRes = demoApi.taskLog("alpha", upid, limit = 10)
        assertNull(logRes.errors)
        val lines = logRes.data.orEmpty()
        assertTrue("Expected task log lines for SDN apply, got empty", lines.isNotEmpty())
        assertTrue(
            "Expected Applying SDN configuration log line",
            lines.any { (it["t"] as? String)?.contains("Applying SDN configuration") == true }
        )
    }

    @Test
    fun awaitTask_completesWithOk() = runBlocking {
        var pollCount = 0
        val fakeTaskApi = object : ProxmoxApi by demoApi {
            override suspend fun taskStatus(node: String, upid: String): PveResponse<TaskStatus> {
                pollCount++
                return if (pollCount < 3) {
                    PveResponse(data = TaskStatus(status = "running", upid = upid))
                } else {
                    PveResponse(data = TaskStatus(status = "stopped", exitstatus = "OK", upid = upid))
                }
            }
        }

        val repository = createRepository(fakeTaskApi)
        val outcome = repository.awaitTask("alpha", "UPID:alpha:00003001:00000000:66D1B010:sdnreload::root@pam:", timeoutMs = 5000L, intervalMs = 10L)
        assertTrue(outcome.isSuccess)
        val finalStatus = outcome.getOrNull()
        assertNotNull(finalStatus)
        assertEquals("stopped", finalStatus?.status)
        assertEquals("OK", finalStatus?.exitstatus)
        assertTrue(finalStatus?.isOk == true)
        assertFalse(finalStatus?.isRunning == true)
        assertTrue(pollCount >= 3)
    }

    // -------------------------------------------------------------------------
    // 3. Syslog map parsing to ClusterLogEntry
    // -------------------------------------------------------------------------

    @Test
    fun syslogMap_parsing_to_ClusterLogEntry() {
        // Standard syslog map with "t" and "n"
        val rawMap1 = mapOf<String, Any>(
            "n" to 42,
            "t" to "systemd[1]: Starting Proxmox VE cluster logger...",
            "time" to 1700000100L,
            "pri" to 6,
            "tag" to "systemd",
            "user" to "root@pam",
            "pid" to 1,
        )

        val entry1 = ClusterLogEntry.fromSyslogMap("nodeA", rawMap1)
        assertEquals("nodeA_42", entry1.id)
        assertEquals("nodeA", entry1.node)
        assertEquals("systemd[1]: Starting Proxmox VE cluster logger...", entry1.msg)
        assertEquals(1700000100L, entry1.time)
        assertEquals(6, entry1.pri)
        assertEquals("systemd", entry1.tag)
        assertEquals("root@pam", entry1.user)
        assertEquals(1L, entry1.pid)

        // Alternate syslog map using "msg" key and String numbers
        val rawMap2 = mapOf<String, Any>(
            "n" to "105",
            "msg" to "pve-firewall[920]: rules updated: 5 rules loaded",
            "time" to "1700000200",
            "pri" to "5",
            "tag" to "pve-firewall",
            "pid" to "920",
        )

        val entry2 = ClusterLogEntry.fromSyslogMap("nodeB", rawMap2)
        assertEquals("nodeB_105", entry2.id)
        assertEquals("nodeB", entry2.node)
        assertEquals("pve-firewall[920]: rules updated: 5 rules loaded", entry2.msg)
        assertEquals(1700000200L, entry2.time)
        assertEquals(5, entry2.pri)
        assertEquals("pve-firewall", entry2.tag)
        assertEquals(920L, entry2.pid)
    }

    @Test
    fun demoApi_nodeSyslog_producesValidEntries() = runBlocking {
        val res = demoApi.nodeSyslog("alpha", limit = 5)
        assertNull(res.errors)
        val rawList = res.data.orEmpty()
        assertEquals(5, rawList.size)

        val entries = rawList.map { ClusterLogEntry.fromSyslogMap("alpha", it) }
        assertEquals(5, entries.size)
        assertTrue(entries.all { it.node == "alpha" })
        assertTrue(entries.all { !it.msg.isNullOrBlank() })
        assertTrue(entries.any { it.tag == "systemd" })
    }

    // -------------------------------------------------------------------------
    // 4. SdnStatusInfo !isOk for pending / error
    // -------------------------------------------------------------------------

    @Test
    fun sdnStatusInfo_isOk_evaluatesPendingAndErrors() {
        // Pending state
        val pendingZone = SdnStatusInfo.fromMap(
            mapOf("zone" to "localnet", "type" to "zone", "status" to "pending", "state" to "pending")
        )
        assertEquals("localnet", pendingZone.name)
        assertEquals("pending", pendingZone.status)
        assertFalse("Pending zone must have isOk == false", pendingZone.isOk)

        // Error state
        val errorZone = SdnStatusInfo.fromMap(
            mapOf("name" to "evpn-bad", "status" to "error")
        )
        assertFalse("Error zone must have isOk == false", errorZone.isOk)

        // Failed state
        val failedZone = SdnStatusInfo.fromMap(
            mapOf("zone" to "vxlan-fail", "status" to "failed")
        )
        assertFalse("Failed zone must have isOk == false", failedZone.isOk)

        // Null status
        val nullStatusZone = SdnStatusInfo(
            name = "unknown-zone",
            type = "zone",
            status = null,
            controller = null,
        )
        assertFalse("Zone with null status must have isOk == false", nullStatusZone.isOk)

        // Healthy ok / running states
        val okZone = SdnStatusInfo.fromMap(
            mapOf("zone" to "vlan10", "type" to "zone", "status" to "ok")
        )
        assertTrue("ok status must have isOk == true", okZone.isOk)

        val runningZone = SdnStatusInfo.fromMap(
            mapOf("zone" to "vlan20", "type" to "zone", "status" to "running")
        )
        assertTrue("running status must have isOk == true", runningZone.isOk)
    }

    // -------------------------------------------------------------------------
    // 5. Settings Hub: Live Cluster Surface & Formatting
    // -------------------------------------------------------------------------

    @Test
    fun demoApi_hubLoad_verifiesLiveCountsAndSubtitles() = runBlocking {
        val repository = createRepository(demoApi)

        // 1. Firewall enable + rule count
        val fwRes = repository.loadClusterFirewall()
        assertTrue(fwRes.isSuccess)
        val fwSnap = fwRes.getOrNull()
        assertNotNull(fwSnap)
        assertTrue(fwSnap!!.enabled)
        assertEquals(5, fwSnap.rules.size)
        val fwSubtitle = SettingsViewModel.formatFirewallSubtitle(fwRes)
        assertEquals("enabled · 5 rules", fwSubtitle)

        // 2. SDN zone and vnet counts
        val zonesRes = repository.listSdnZones()
        val vnetsRes = repository.listSdnVnets()
        assertTrue(zonesRes.isSuccess)
        assertTrue(vnetsRes.isSuccess)
        assertEquals(3, zonesRes.getOrNull()?.size)
        assertEquals(3, vnetsRes.getOrNull()?.size)
        val sdnSubtitle = SettingsViewModel.formatSdnSubtitle(zonesRes, vnetsRes)
        assertEquals("3 zones · 3 vnets", sdnSubtitle)

        // 3. logHistory non-empty
        val logsRes = repository.logHistory(max = 20)
        assertTrue(logsRes.isSuccess)
        val logs = logsRes.getOrNull().orEmpty()
        assertTrue("logHistory must be non-empty", logs.isNotEmpty())
        val logsSubtitle = SettingsViewModel.formatLogsSubtitle(logsRes)
        assertEquals("${logs.size} events", logsSubtitle)

        // 4. updates pending >= 0
        val updatesRes = repository.listClusterUpdates()
        assertTrue(updatesRes.isSuccess)
        val updateSnapshots = updatesRes.getOrNull().orEmpty()
        assertTrue("update snapshots must be non-empty", updateSnapshots.isNotEmpty())
        val pendingCount = updateSnapshots.sumOf { it.updateCount }
        assertTrue("pending count must be >= 0", pendingCount >= 0)
        val updatesSubtitle = SettingsViewModel.formatUpdatesSubtitle(updatesRes)
        assertEquals("$pendingCount pending", updatesSubtitle)

        // 5. SettingsViewModel populates typed fields and subtitles
        val vm = SettingsViewModel(repository, coroutineScope = CoroutineScope(Dispatchers.Default))
        vm.pollCheap()
        vm.loadUpdates()
        val state = vm.state
        assertEquals("enabled · 5 rules", state.firewallSubtitle)
        assertEquals("3 zones · 3 vnets", state.sdnSubtitle)
        assertEquals("${logs.size} events", state.logsSubtitle)
        assertEquals("$pendingCount pending", state.updatesSubtitle)
        assertNotNull(state.firewallSnapshot)
        assertNotNull(state.sdnZones)
        assertNotNull(state.sdnVnets)
        assertNotNull(state.logEntries)
        assertNotNull(state.updateSnapshots)
    }

    @Test
    fun simulated501_onSdnZones_producesNotConfiguredSubtitle_neverZeroZones() = runBlocking {
        val fakeApi501 = object : ProxmoxApi by demoApi {
            override suspend fun sdnZones(): PveResponse<List<Map<String, Any>>> {
                throw PveHttpException(code = 501, errorBody = null, httpMessage = "not implemented")
            }
        }

        val repository = createRepository(fakeApi501)
        val zonesRes = repository.listSdnZones()
        val vnetsRes = repository.listSdnVnets()
        assertTrue(zonesRes.isFailure)

        val sdnSubtitle = SettingsViewModel.formatSdnSubtitle(zonesRes, vnetsRes)
        assertEquals("not configured", sdnSubtitle)
        assertFalse("Must never show '0 zones' on 501", sdnSubtitle.contains("0 zones"))
        assertFalse("Must never show '0' on 501", sdnSubtitle.contains("0"))

        // Also test through ViewModel
        val vm = SettingsViewModel(repository, coroutineScope = CoroutineScope(Dispatchers.Default))
        vm.pollCheap()
        val state = vm.state
        assertEquals("not configured", state.sdnSubtitle)
        assertFalse("Must never show '0 zones' on 501 in ViewModel state", state.sdnSubtitle.contains("0 zones"))
        assertFalse("Must never show '0' on 501 in ViewModel state", state.sdnSubtitle.contains("0"))
    }

    @Test
    fun simulated403_onSdnZones_producesPermissionSubtitle_neverZeroZones() = runBlocking {
        val fakeApi403 = object : ProxmoxApi by demoApi {
            override suspend fun sdnZones(): PveResponse<List<Map<String, Any>>> {
                throw PveHttpException(code = 403, errorBody = null, httpMessage = "forbidden")
            }
        }

        val repository = createRepository(fakeApi403)
        val zonesRes = repository.listSdnZones()
        val vnetsRes = repository.listSdnVnets()
        assertTrue(zonesRes.isFailure)

        val sdnSubtitle = SettingsViewModel.formatSdnSubtitle(zonesRes, vnetsRes)
        assertEquals("permission required", sdnSubtitle)
        assertFalse("Must never show '0 zones' on 403", sdnSubtitle.contains("0 zones"))

        // Also test through ViewModel
        val vm = SettingsViewModel(repository, coroutineScope = CoroutineScope(Dispatchers.Default))
        vm.pollCheap()
        val state = vm.state
        assertEquals("permission required", state.sdnSubtitle)
        assertFalse("Must never show '0 zones' on 403 in ViewModel state", state.sdnSubtitle.contains("0 zones"))
    }

    // -------------------------------------------------------------------------
    // 6. Datacenter Firewall Enable Refusal on Empty Rules
    // -------------------------------------------------------------------------

    @Test
    fun clusterFirewall_enable_withEmptyRules_isRefused_andDoesNotCallPut() = runBlocking {
        var putCalled = false
        val fakeApi = object : ProxmoxApi by demoApi {
            override suspend fun clusterFirewallRules(): PveResponse<List<Map<String, Any>>> =
                PveResponse(data = emptyList())

            override suspend fun setClusterFirewallOptions(enable: Int, digest: String?): PveResponse<String?> {
                putCalled = true
                fail("setClusterFirewallOptions PUT must not be called when empty rules")
                return PveResponse(data = null)
            }
        }

        val repository = createRepository(fakeApi)
        val vm = FirewallViewModel(repository, coroutineScope = CoroutineScope(Dispatchers.Default))
        vm.fetchData()

        val job = vm.setFirewallEnabled(true)
        assertNull("Job should be null when enable is refused", job)
        assertFalse("PUT setClusterFirewallOptions must not be called when empty rules", putCalled)
        assertEquals(
            FirewallViewModel.REFUSAL_EMPTY_FIREWALL_MESSAGE,
            vm.state.actionError,
        )
    }

    @Test
    fun clusterFirewall_enable_withAcceptRule_callsPut_andSucceeds() = runBlocking {
        var putCalled = false
        var passedEnable: Int? = null
        val fakeApi = object : ProxmoxApi by demoApi {
            override suspend fun setClusterFirewallOptions(enable: Int, digest: String?): PveResponse<String?> {
                putCalled = true
                passedEnable = enable
                return demoApi.setClusterFirewallOptions(enable, digest)
            }
        }

        val repository = createRepository(fakeApi)
        val vm = FirewallViewModel(repository, coroutineScope = CoroutineScope(Dispatchers.Default))
        vm.fetchData()

        val job = vm.setFirewallEnabled(true)
        assertNotNull("Job should not be null when enable is permitted", job)
        job?.join()
        assertTrue("PUT setClusterFirewallOptions must be called when rules contain an enabled ACCEPT rule", putCalled)
        assertEquals(1, passedEnable)
        assertNull(vm.state.actionError)
    }

    @Test
    fun clusterFirewall_disable_withEmptyRules_isAllowed_andCallsPut() = runBlocking {
        var putCalled = false
        var passedEnable: Int? = null
        val fakeApi = object : ProxmoxApi by demoApi {
            override suspend fun clusterFirewallRules(): PveResponse<List<Map<String, Any>>> =
                PveResponse(data = emptyList())

            override suspend fun setClusterFirewallOptions(enable: Int, digest: String?): PveResponse<String?> {
                putCalled = true
                passedEnable = enable
                return PveResponse(data = null)
            }
        }

        val repository = createRepository(fakeApi)
        val vm = FirewallViewModel(repository, coroutineScope = CoroutineScope(Dispatchers.Default))
        vm.fetchData()

        val job = vm.setFirewallEnabled(false)
        assertNotNull("Job should not be null when disable is permitted", job)
        job?.join()
        assertTrue("PUT setClusterFirewallOptions must be called on cluster disable even with empty rules", putCalled)
        assertEquals(0, passedEnable)
        assertNull(vm.state.actionError)
    }

    @Test
    fun settingsViewModel_initialCollection_triggersImmediatePoll() = runBlocking {
        var callCount = 0
        val countingApi = object : ProxmoxApi by demoApi {
            override suspend fun clusterFirewallOptions(): PveResponse<Map<String, Any>> {
                callCount++
                return demoApi.clusterFirewallOptions()
            }
        }

        val repository = createRepository(countingApi)
        val vm = SettingsViewModel(repository, coroutineScope = CoroutineScope(Dispatchers.Default))
        // SettingsViewModel init launches loadAll (calling clusterFirewallOptions once).
        // With tickerFlow(emitImmediately = true), collecting ui triggers pollFlow immediately,
        // invoking pollCheap without waiting for the 6500ms ticker interval.
        val collectJob = launch {
            vm.ui.collect { }
        }
        delay(150)
        collectJob.cancel()

        assertTrue("Expected immediate poll upon collection without waiting 6500ms, got: $callCount", callCount >= 2)
    }
}
