package com.pxmx.app.data.ssh

import android.content.ContextWrapper
import com.pxmx.app.data.FakeSharedPreferences
import com.pxmx.app.data.api.DemoApi
import com.pxmx.app.data.api.ProbeApi
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.PveResponse
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.repo.PveClient
import com.pxmx.app.data.repo.StorageRepository
import com.pxmx.app.data.repo.UpdateRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSshTargetTest {

    // Documentation addresses only (RFC 5737: 192.0.2.0/24, 198.51.100.0/24)

    @Test
    fun localNodeUsesSessionHost() {
        val host = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = true,
            sessionHost = "192.0.2.10",
            reportedAddresses = emptyList(),
        )
        assertEquals("192.0.2.10", host)

        // With scheme and port in session host
        val hostWithPort = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = true,
            sessionHost = "https://192.0.2.10:8006",
            reportedAddresses = emptyList(),
        )
        assertEquals("192.0.2.10", hostWithPort)
    }

    @Test
    fun remoteNodeWithNoContainingCidrKeepsCorosync() {
        // Remote node reporting an interface in 198.51.100.0/24 which does NOT contain 192.0.2.10
        val host = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(cidr = "198.51.100.2/24"),
            ),
        )
        assertEquals("198.51.100.1", host)

        // Remote node reporting empty addresses
        val hostEmpty = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = emptyList(),
        )
        assertEquals("198.51.100.1", hostEmpty)
    }

    @Test
    fun remoteNodeWhoseReportedCidrContainsSessionHostUsesThatInterfaceAddress() {
        // Session host is 192.0.2.10. Remote node reports 192.0.2.20/24 (which contains 192.0.2.10).
        val host = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(cidr = "192.0.2.20/24"),
            ),
        )
        assertEquals("192.0.2.20", host)

        // Also works via address + netmask (dotted decimal)
        val hostMask = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(address = "192.0.2.20", netmask = "255.255.255.0"),
            ),
        )
        assertEquals("192.0.2.20", hostMask)

        // Also works via address + netmask prefix length string
        val hostPrefix = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(address = "192.0.2.20", netmask = "24"),
            ),
        )
        assertEquals("192.0.2.20", hostPrefix)
    }

    @Test
    fun twoContainingCidrsAndNoneEqualToSessionHostKeepsCorosync() {
        // Both 192.0.2.20/24 and 192.0.2.30/24 contain session host 192.0.2.10, but neither equals 192.0.2.10
        val host = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(cidr = "192.0.2.20/24"),
                NodeReportedAddress(cidr = "192.0.2.30/24"),
            ),
        )
        assertEquals("198.51.100.1", host)

        // If one of multiple containing CIDRs equals the session host, use it
        val hostMatch = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(cidr = "192.0.2.10/24"),
                NodeReportedAddress(cidr = "192.0.2.30/24"),
            ),
        )
        assertEquals("192.0.2.10", hostMatch)
    }

    @Test
    fun demoIsNotPassedIntoTheChooserByTheRepositories() = runBlocking {
        var chooserInvoked = false
        val demo = DemoApi()
        val store = SessionStore(injectedPrefs = FakeSharedPreferences())
        val demoConfig = ServerConfig(
            host = "demo",
            username = "demo",
            password = "demo",
            authMode = AuthMode.PASSWORD,
        )
        store.setSession(SessionState(config = demoConfig, username = "demo", ticket = "demo-ticket"))

        val provider = object : ProxmoxApiProvider {
            override fun apiFor(config: ServerConfig): ProxmoxApi = object : ProxmoxApi by demo {
                override suspend fun clusterStatus(): PveResponse<List<Map<String, Any>>> {
                    chooserInvoked = true
                    return demo.clusterStatus()
                }
            }
            override fun apiForProbe(config: ServerConfig): ProbeApi = ProbeApi(demo)
            override fun clear() {}
        }
        val client = PveClient(ContextWrapper(null), store, provider)

        // 1. UpdateRepository: sshUpgrade with demo config triggers simulateDemoSshUpgrade early
        val updateRepo = UpdateRepository(store, client, { listOf("node1") })
        val updateResult = updateRepo.sshUpgrade("node1")
        assertTrue(updateResult.isSuccess)
        assertFalse("UpdateRepository must not invoke cluster status / chooser in demo mode", chooserInvoked)

        // 2. StorageRepository: backupToDevice fails on rootProfile check before any SSH resolution
        val storageRepo = StorageRepository(
            context = ContextWrapper(null),
            sessionStore = store,
            pveClient = client,
            taskStatusProvider = { _, _ -> Result.success(TaskStatus(status = "stopped", exitstatus = "OK")) },
        )
        val backupResult = storageRepo.backupToDevice("node1", "qemu", 100L, "local") {}
        assertTrue(backupResult.isFailure)
        assertFalse("StorageRepository must not invoke cluster status / chooser in demo mode", chooserInvoked)

        // 3. Chooser itself directly rejects "demo" as session host on a local node
        val hostFromChooser = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = true,
            sessionHost = "demo",
            reportedAddresses = emptyList(),
        )
        assertEquals("198.51.100.1", hostFromChooser)
    }

    @Test
    fun doesNotInventSlash24WhenOnlyAddressIsReported() {
        // Address without CIDR prefix or netmask must not invent a /24
        val host = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(address = "192.0.2.20"),
            ),
        )
        assertEquals("198.51.100.1", host)
    }

    @Test
    fun nonIpSessionHostKeepsCorosyncOnRemoteNode() {
        val host = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "pve.example.com",
            reportedAddresses = listOf(
                NodeReportedAddress(cidr = "192.0.2.20/24"),
            ),
        )
        assertEquals("198.51.100.1", host)
    }

    @Test
    fun remoteNodeNeverAdoptsLoginHostDirectly() {
        // Rule 3: Never SSH a remote node at login host just because login worked
        val host = chooseNodeSshHost(
            corosyncIp = "198.51.100.1",
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = listOf(
                NodeReportedAddress(cidr = "192.0.2.20/24"),
            ),
        )
        // Uses the interface's host part, NOT the login host
        assertEquals("192.0.2.20", host)
        assertFalse(host == "192.0.2.10")
    }

    @Test
    fun missingUsableAddressReturnsNull() {
        val host = chooseNodeSshHost(
            corosyncIp = null,
            isLocal = false,
            sessionHost = "192.0.2.10",
            reportedAddresses = emptyList(),
        )
        assertNull(host)
    }

    @Test
    fun stripSchemeAndPortVariants() {
        assertEquals("192.0.2.10", stripSchemeAndPort("192.0.2.10"))
        assertEquals("192.0.2.10", stripSchemeAndPort("192.0.2.10:8006"))
        assertEquals("192.0.2.10", stripSchemeAndPort("https://192.0.2.10:8006/"))
        assertEquals("192.0.2.10", stripSchemeAndPort("http://192.0.2.10:8006/api2/json"))
        assertEquals("pve.example.com", stripSchemeAndPort("https://pve.example.com:8006"))
        assertEquals("2001:db8::1", stripSchemeAndPort("[2001:db8::1]:8006"))
        assertEquals("2001:db8::1", stripSchemeAndPort("2001:db8::1"))
        assertEquals("demo", stripSchemeAndPort("demo"))
    }
}
