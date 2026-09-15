package com.pxmx.app.data.repo

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.*
import com.pxmx.app.data.model.*
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test

class OperationsRegressionTest {
    private val demo = DemoApi()
    private val store = SessionStore(injectedPrefs = FakeSharedPreferences()).apply {
        setSession(SessionState(ServerConfig(host = "entry.example"), ticket = "test-ticket", username = "root@pam"))
    }
    private fun client(api: ProxmoxApi) = PveClient(ContextWrapper(null), store, provider(api))
    private fun provider(api: ProxmoxApi) = object : ProxmoxApiProvider {
        override fun apiFor(config: ServerConfig) = api
        override fun apiForProbe(config: ServerConfig) = ProbeApi(api)
        override fun clear() {}
    }
    private fun repository(api: ProxmoxApi) = ProxmoxRepository(ContextWrapper(null), store, provider(api))

    @Test fun backupSftpUsesSelectedNodeAndVerifiedRootPassword() = runBlocking {
        val config = ServerConfig(host = "entry.example", username = "root@pam", password = "fake-root-password")
        store.saveProfileFromLogin(config, saveCredentials = true)
        store.setSession(SessionState(config.copy(password = ""), ticket = "fake", username = "root@pam"))
        val nodes = mutableListOf<String>()
        val api = object : ProxmoxApi by demo {
            override suspend fun clusterStatus() = PveResponse(data = listOf(mapOf<String, Any>("name" to "beta", "type" to "node", "ip" to "192.0.2.2")))
            override suspend fun storageContent(node: String, storage: String, content: String?, vmid: Long?): PveResponse<List<StorageContentItem>> {
                nodes += node
                return demo.storageContent(node, storage, content, vmid)
            }
            override suspend fun storageVolume(node: String, storage: String, volume: String): PveResponse<StorageContentItem> {
                nodes += node
                return demo.storageVolume(node, storage, volume)
            }
        }
        val contacted = mutableListOf<String>()
        val sftp = object : com.pxmx.app.data.ssh.SftpDownloader({ null }, { _, _ -> }) {
            override suspend fun download(host: String, port: Int, username: String, password: String, remotePath: String, localSink: java.io.OutputStream, onProgress: (Long, Long) -> Unit) {
                contacted += "$host/$username"
                assertEquals("fake-root-password", password)
                assertTrue(remotePath.startsWith("/var/lib/vz/dump/"))
            }
        }
        val storage = StorageRepository(ContextWrapper(null), store, client(api),
            { _, _ -> Result.success(TaskStatus(status = "stopped", exitstatus = "OK")) }, sftp,
            { _, block -> block(java.io.ByteArrayOutputStream()); Result.success(Unit) })
        storage.backupToDevice("beta", "qemu", 100, "local") {}.getOrThrow()
        assertEquals(listOf("beta", "beta"), nodes)
        assertEquals(listOf("192.0.2.2/root"), contacted)
    }

    @Test fun sshUpgradeUsesSelectedNodeIpv6NotEntryHost() = runBlocking {
        val config = ServerConfig(host = "entry.example", username = "root", password = "fake-root-password")
        store.saveProfileFromLogin(config, saveCredentials = true)
        store.setSession(SessionState(config.copy(password = ""), ticket = "fake", username = "root@pam"))
        val api = object : ProxmoxApi by demo {
            override suspend fun clusterStatus() = PveResponse(data = listOf(
                mapOf<String, Any>("name" to "alpha", "type" to "node", "ip" to "192.0.2.1", "local" to 1),
                mapOf<String, Any>("name" to "beta", "type" to "node", "ip" to "2001:db8::2"),
            ))
        }
        var contacted: String? = null
        val executor = object : com.pxmx.app.data.ssh.SshUpgradeExecutor({ null }, { _, _ -> }) {
            override suspend fun executeUpgrade(host: String, port: Int, username: String, password: String, command: String, onOutputLine: (String) -> Unit): Result<Int> {
                contacted = host
                assertEquals("root", username)
                assertEquals("fake-root-password", password)
                return Result.success(0)
            }
        }
        UpdateRepository(store, client(api), { listOf("alpha", "beta") }, executor).sshUpgrade("beta").getOrThrow()
        assertEquals("2001:db8::2", contacted)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun sshAvailabilityRejectsNonRootPamAndMismatchedActiveProfile() = kotlinx.coroutines.test.runTest {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        try {
            val root = ServerConfig(host = "entry.example", username = "root", password = "fake")
            val cases = listOf(
                root.copy(username = "admin") to root.copy(username = "admin"),
                root.copy(realm = "pve") to root.copy(realm = "pve"),
                root.copy(port = 443) to root,
                root.copy(host = "other.example") to root,
                root.copy(username = "admin") to root,
                root.copy(realm = "pve") to root,
                root.copy(authMode = AuthMode.API_TOKEN, apiToken = "fake-token") to root,
            )
            val incorrectlyAvailable = cases.filter { (saved, active) ->
                store.saveProfileFromLogin(saved, saveCredentials = true)
                store.setSession(SessionState(active.copy(password = ""), ticket = "fake", username = "${active.username}@${active.realm}"))
                com.pxmx.app.ui.settings.UpdatesViewModel(repository(demo), store).ui.value.isPasswordAuth
            }
            assertTrue("Only an exact active root PAM password profile qualifies: ${incorrectlyAvailable.size} invalid identities accepted", incorrectlyAvailable.isEmpty())
        } finally { kotlinx.coroutines.Dispatchers.resetMain() }
    }

    @Test fun deletionWaitsForTaskAndReportsFailure() = kotlinx.coroutines.test.runTest {
        var polls = 0
        val api = object : ProxmoxApi by demo {
            override suspend fun deleteStorageContent(node: String, storage: String, volume: String) = PveResponse<String>(data = "UPID:alpha:imgdel")
            override suspend fun taskStatus(node: String, upid: String): PveResponse<TaskStatus> {
                polls++
                return PveResponse(data = if (polls == 1) TaskStatus(status = "running") else TaskStatus(status = "stopped", exitstatus = "permission denied"))
            }
        }
        val result = repository(api).deleteStorageVolume("alpha", "local:backup/test.vma.zst")
        assertTrue("A failed imgdel task must not be reported as Deleted", result.isFailure)
        assertEquals(2, polls)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("permission denied"))
    }

    @Test fun usbDigestConflictRereadsAndReallocates() = runBlocking {
        var reads = 0
        val writes = mutableListOf<Map<String, String>>()
        val api = object : ProxmoxApi by demo {
            override suspend fun guestConfig(node: String, type: String, vmid: Long, current: Int?): PveResponse<Map<String, Any>> {
                reads++
                return PveResponse(data = if (reads == 1) mapOf("digest" to "first") else mapOf("usb0" to "host=other", "digest" to "second"))
            }
            override suspend fun updateGuestConfig(node: String, type: String, vmid: Long, fields: Map<String, String>): PveResponse<String?> {
                writes += fields
                if (writes.size == 1) throw PveHttpException(500, "checksum mismatch (file change by other user?)", null)
                return PveResponse(data = "OK")
            }
        }
        val result = repository(api).attachUsb("alpha", GuestType.QEMU, 100, "1234:5678")
        assertTrue("A digest conflict should trigger a fresh allocation", result.isSuccess)
        assertEquals(2, reads)
        assertEquals("second", writes.last()["digest"])
        assertTrue(writes.last().containsKey("usb1"))
    }

    @Test fun usbAllocationIncludesPendingConfigAndDigest() = runBlocking {
        var fields: Map<String, String>? = null
        val api = object : ProxmoxApi by demo {
            override suspend fun guestConfig(node: String, type: String, vmid: Long, current: Int?) =
                PveResponse(data = if (current == 0) mapOf<String, Any>("usb0" to "host=old", "digest" to "version1") else mapOf("digest" to "version1"))
            override suspend fun updateGuestConfig(node: String, type: String, vmid: Long, values: Map<String, String>): PveResponse<String?> {
                fields = values
                return PveResponse(data = "OK")
            }
        }
        repository(api).attachUsb("alpha", GuestType.QEMU, 100, "1234:5678").getOrThrow()
        assertEquals("host=1234:5678,usb3=1", fields?.get("usb1"))
        assertEquals("version1", fields?.get("digest"))
        assertFalse(fields.orEmpty().containsKey("usb0"))
    }

    @Test fun sdnStatusRetainsDistinctNodeIdentity() = runBlocking {
        val api = object : ProxmoxApi by demo {
            override suspend fun nodeSdnZones(node: String) = PveResponse(data = listOf(mapOf<String, Any>("zone" to "shared", "type" to "simple", "status" to "ok")))
        }
        val rows = NetworkRepository(client(api)) { listOf("alpha", "beta") }.listSdnStatus().getOrThrow()
        assertEquals(2, rows.size)
        assertEquals(listOf("alpha", "beta"), rows.map { it.node })
        assertEquals(2, rows.map { it.rowKey }.distinct().size)
        assertNotEquals("Same zone on different nodes must have different identities", rows[0], rows[1])
    }
}
