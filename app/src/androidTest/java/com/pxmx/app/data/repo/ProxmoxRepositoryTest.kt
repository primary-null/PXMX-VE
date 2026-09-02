package com.pxmx.app.data.repo

import androidx.test.core.app.ApplicationProvider
import com.pxmx.app.data.api.ProxmoxApi
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.*
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ProxmoxRepositoryTest {

    private lateinit var sessionStore: SessionStore
    private lateinit var repository: ProxmoxRepository
    private lateinit var fakeApi: FakeProxmoxApi
    private lateinit var apiProvider: FakeApiProvider

    @Before
    fun setup() {
        sessionStore = SessionStore(ApplicationProvider.getApplicationContext())
        fakeApi = FakeProxmoxApi()
        apiProvider = FakeApiProvider(fakeApi)
        repository = ProxmoxRepository(
            ApplicationProvider.getApplicationContext(),
            sessionStore,
            apiProvider,
        )
    }

    @Test
    fun apiCall_retriesOnce_after401_andSucceeds() = runBlocking {
        val config = ServerConfig(
            host = "localhost",
            authMode = AuthMode.PASSWORD,
            username = "root",
            password = "password"
        )
        
        // Setup initial session and saved profile
        sessionStore.saveProfileFromLogin(config, saveCredentials = true)
        val initialSession = SessionState(config, ticket = "old-ticket")
        sessionStore.setSession(initialSession)

        // First call to version() will fail with 401, second (during retry) will succeed
        fakeApi.failWith401Once = true
        // createTicket (for re-mint) must also succeed
        fakeApi.ticketResponse = PveResponse(data = TicketData(ticket = "new-ticket", csrfPreventionToken = "new-csrf"))
        fakeApi.versionResponse = PveResponse(data = VersionInfo(version = "8.0.0"))

        val result = repository.refreshVersion()

        assertTrue(result.isSuccess)
        assertEquals("8.0.0", result.getOrNull()?.version)
        assertEquals("new-ticket", sessionStore.session.value?.ticket)
        assertEquals(1, fakeApi.createTicketCallCount)
        // 1 (401 attempt) + 1 (retry after direct ticket renewal)
        assertEquals(2, fakeApi.versionCallCount)
    }

    @Test
    fun login_detectsTfaChallenge_returnsNeedsTfa() = runBlocking {
        val config = ServerConfig(
            host = "localhost",
            authMode = AuthMode.PASSWORD,
            username = "tfa-user",
            password = "password"
        )
        fakeApi.failCreateTicketWith401Tfa = true

        val outcome = repository.login(config)

        assertTrue(outcome is LoginOutcome.NeedsTfa)
        val needsTfa = outcome as LoginOutcome.NeedsTfa
        assertEquals("PVE:tfa-user@pam:TFA_PARTIAL", needsTfa.partialTicket)
    }

    @Test
    fun login_200NeedTfa_returnsNeedsTfa() = runBlocking {
        val config = ServerConfig(
            host = "localhost",
            authMode = AuthMode.PASSWORD,
            username = "tfa-user",
            password = "password"
        )
        fakeApi.ticketResponse = PveResponse(
            data = TicketData(
                ticket = "PVE:tfa-user@pam!tfa!WRAPPED",
                csrfPreventionToken = "partial-csrf",
                username = "tfa-user@pam",
                needTfa = 1,
            )
        )

        val outcome = repository.login(config)

        assertTrue(outcome is LoginOutcome.NeedsTfa)
        val needsTfa = outcome as LoginOutcome.NeedsTfa
        assertEquals("PVE:tfa-user@pam!tfa!WRAPPED", needsTfa.partialTicket)
    }

    @Test
    fun completeTfa_withValidOtp_establishesSession() = runBlocking {
        val config = ServerConfig(
            host = "localhost",
            authMode = AuthMode.PASSWORD,
            username = "tfa-user",
            password = "password"
        )
        fakeApi.tfaResponse = PveResponse(
            data = TicketData(ticket = "full-tfa-ticket", csrfPreventionToken = "full-csrf", username = "tfa-user@pam")
        )
        fakeApi.versionResponse = PveResponse(data = VersionInfo(version = "8.3.0"))

        val result = repository.completeTfa(
            config = config,
            partialTicket = "PVE:tfa-user@pam:TFA_PARTIAL",
            otp = "123456"
        )

        assertTrue(result.isSuccess)
        assertEquals("totp:123456", fakeApi.lastTfaPassword)
        assertEquals("full-tfa-ticket", sessionStore.session.value?.ticket)
        assertEquals("full-csrf", sessionStore.session.value?.csrf)
    }

    @Test
    fun login_capturesAndSavesCertPin_whenTrustSelfSigned() = runBlocking {
        val config = ServerConfig(
            host = "192.0.2.100",
            authMode = AuthMode.PASSWORD,
            username = "root",
            password = "password",
            trustSelfSigned = true
        )
        fakeApi.ticketResponse = PveResponse(data = TicketData(ticket = "test-ticket", csrfPreventionToken = "csrf"))
        fakeApi.versionResponse = PveResponse(data = VersionInfo(version = "8.3.0"))
        apiProvider.fingerprintToReturn = "AA:BB:CC:DD:EE:FF:11:22:33:44"

        val outcome = repository.login(config)
        assertTrue(outcome is LoginOutcome.Success)
        assertEquals("AA:BB:CC:DD:EE:FF:11:22:33:44", sessionStore.getCertPin("192.0.2.100"))
    }

    private class FakeApiProvider(val api: ProxmoxApi) : ProxmoxApiProvider {
        var fingerprintToReturn: String? = null
        override fun apiFor(config: ServerConfig): ProxmoxApi = api
        override fun clear() {}
        override fun getCapturedFingerprint(host: String): String? = fingerprintToReturn
    }

    private class FakeProxmoxApi : ProxmoxApi {
        var failWith401Once = false
        var failCreateTicketWith401Tfa = false
        var versionCallCount = 0
        var createTicketCallCount = 0
        
        var ticketResponse: PveResponse<TicketData> = PveResponse()
        var tfaResponse: PveResponse<TicketData> = PveResponse()
        var versionResponse: PveResponse<VersionInfo> = PveResponse()

        override suspend fun createTicket(username: String, password: String): PveResponse<TicketData> {
            createTicketCallCount++
            if (failCreateTicketWith401Tfa) {
                val errorBody = "{\"data\":{\"ticket\":\"PVE:tfa-user@pam:TFA_PARTIAL\",\"NeedTFA\":1}}".toResponseBody("application/json".toMediaType())
                throw HttpException(Response.error<Any>(401, errorBody))
            }
            return ticketResponse
        }

        var lastTfaPassword: String? = null

        override suspend fun createTicketTfa(username: String, password: String, tfaChallenge: String): PveResponse<TicketData> {
            lastTfaPassword = password
            return tfaResponse
        }

        override suspend fun version(): PveResponse<VersionInfo> {
            versionCallCount++
            if (failWith401Once) {
                failWith401Once = false
                val errorBody = "{\"message\":\"Authentication failed\"}".toResponseBody("application/json".toMediaType())
                throw HttpException(Response.error<Any>(401, errorBody))
            }
            return versionResponse
        }

        // Stub other methods as needed or use a mock library if available. 
        // For this test, only createTicket and version are needed for refreshVersion().
        
        override suspend fun clusterResources(type: String?): PveResponse<List<ClusterResource>> = PveResponse()
        override suspend fun clusterStatus(): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun nodes(): PveResponse<List<ClusterResource>> = PveResponse()
        override suspend fun nodeStatus(node: String): PveResponse<NodeStatus> = PveResponse()
        override suspend fun nodeQemu(node: String): PveResponse<List<ClusterResource>> = PveResponse()
        override suspend fun nodeLxc(node: String): PveResponse<List<ClusterResource>> = PveResponse()
        override suspend fun nodeStorage(node: String): PveResponse<List<NodeStorageEntry>> = PveResponse()
        override suspend fun guestStatus(node: String, type: String, vmid: Long): PveResponse<GuestStatus> = PveResponse()
        override suspend fun cloneGuest(
            node: String,
            type: String,
            vmid: Long,
            newid: Long,
            name: String?,
            hostname: String?
        ): PveResponse<String> = PveResponse()
        override suspend fun guestAction(node: String, type: String, vmid: Long, action: String): PveResponse<String> = PveResponse()
        override suspend fun guestConfig(node: String, type: String, vmid: Long, current: Int?): PveResponse<Map<String, Any>> = PveResponse()
        override suspend fun guestSnapshots(node: String, type: String, vmid: Long): PveResponse<List<SnapshotInfo>> = PveResponse()
        override suspend fun createSnapshot(node: String, type: String, vmid: Long, snapname: String, description: String?, vmstate: Int?): PveResponse<String> = PveResponse()
        override suspend fun deleteSnapshot(node: String, type: String, vmid: Long, snap: String, force: Int?): PveResponse<String> = PveResponse()
        override suspend fun rollbackSnapshot(node: String, type: String, vmid: Long, snap: String): PveResponse<String> = PveResponse()
        override suspend fun storageContent(node: String, storage: String, content: String?, vmid: Long?): PveResponse<List<StorageContentItem>> = PveResponse()
        override suspend fun storageVolume(node: String, storage: String, volume: String): PveResponse<StorageContentItem> = PveResponse()
        override suspend fun storageStatus(node: String, storage: String): PveResponse<StorageStatus> = PveResponse()
        override suspend fun createBackup(node: String, vmid: Long, storage: String, mode: String?, compress: String?, remove: Int?, notesTemplate: String?): PveResponse<String> = PveResponse()
        override suspend fun deleteStorageContent(node: String, storage: String, volume: String): PveResponse<String> = PveResponse()
        override suspend fun nodeUsb(node: String): PveResponse<List<HostUsbDevice>> = PveResponse()
        override suspend fun updateGuestConfig(node: String, type: String, vmid: Long, fields: Map<String, String>): PveResponse<String?> = PveResponse()
        override suspend fun qemuVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> = PveResponse()
        override suspend fun lxcTermProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> = PveResponse()
        override suspend fun nodeTermProxy(node: String, cmd: String?, cmdOpts: String?): PveResponse<ConsoleProxyData> = PveResponse()
        override suspend fun lxcVncProxy(node: String, vmid: Long, websocket: Int): PveResponse<ConsoleProxyData> = PveResponse()
        override suspend fun taskStatus(node: String, upid: String): PveResponse<TaskStatus> = PveResponse()
        override suspend fun clusterTasks(): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun clusterLog(max: Int, since: Long?): PveResponse<List<ClusterLogEntry>> = PveResponse()
        override suspend fun nodeNetwork(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun aptUpdateList(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun aptVersions(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun aptUpdateRefresh(node: String): PveResponse<String> = PveResponse()
        override suspend fun aptUpgrade(node: String): PveResponse<String> = PveResponse()
        override suspend fun taskLog(node: String, upid: String, start: Int?, limit: Int?): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun sdnZones(): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun sdnVnets(): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun sdnStatus(): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun nodeServices(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun nodeTasks(node: String, start: Int?, limit: Int?): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun clusterFirewallOptions(): PveResponse<Map<String, Any>> = PveResponse()
        override suspend fun clusterFirewallRules(): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun clusterFirewallAliases(): PveResponse<List<Map<String, Any>>> = PveResponse()
        override suspend fun nodeFirewallOptions(node: String): PveResponse<Map<String, Any>> = PveResponse()
        override suspend fun nodeFirewallRules(node: String): PveResponse<List<Map<String, Any>>> = PveResponse()
    }
}
