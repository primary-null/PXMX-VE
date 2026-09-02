package com.pxmx.app.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.CookieManager
import com.pxmx.app.data.api.AppJson
import com.pxmx.app.data.api.AuthInterceptor
import com.pxmx.app.data.api.CertUtils
import com.pxmx.app.data.api.DemoShell
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.session.ProbeAuth
import com.pxmx.app.ui.util.Toasts
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.pxmx.app.data.net.ConnectionTestResult
import com.pxmx.app.data.net.LocalNet
import com.pxmx.app.data.ssh.SftpDownloader
import com.pxmx.app.data.ssh.SshUpgradeExecutor
import com.pxmx.app.data.config.GuestConfigParser
import com.pxmx.app.data.model.AptPackageUpdate
import com.pxmx.app.data.model.AptPackageVersion
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ConsoleSession
import com.pxmx.app.data.model.FirewallAlias
import com.pxmx.app.data.model.FirewallRule
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestBundle
import com.pxmx.app.data.model.GuestStatus
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.LoginOutcome
import com.pxmx.app.data.model.NetworkIface
import com.pxmx.app.data.model.NodeBundle
import com.pxmx.app.data.model.NodeNetworkSnapshot
import com.pxmx.app.data.model.NodeServiceInfo
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeStorageEntry
import com.pxmx.app.data.model.NodeTaskInfo
import com.pxmx.app.data.model.NodeUpdateSnapshot
import com.pxmx.app.data.model.ParsedGuestConfig
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.ServerProbe
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.SiteInfo
import com.pxmx.app.data.model.SnapshotInfo
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.data.model.StorageDetail
import com.pxmx.app.data.model.StorageStatus
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.model.VersionInfo
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class ActiveAptTask(
    val node: String,
    val upid: String,
    val type: String,
    val startTimeMs: Long = System.currentTimeMillis(),
)

class ProxmoxRepository(
    private val context: Context,
    val sessionStore: SessionStore,
    private val clientFactory: ProxmoxApiProvider,
    val localNet: LocalNet = LocalNet(context, sessionStore),
) {
    val appContext: Context get() = context

    private val authMutex = Mutex()
    private val repoScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val recentActionRegistry = RecentActionRegistry()

    private val _clusterLogCache = MutableStateFlow<List<ClusterLogEntry>>(emptyList())
    val clusterLogCache: StateFlow<List<ClusterLogEntry>> = _clusterLogCache.asStateFlow()

    private val _isUpdatesScreenActive = MutableStateFlow(false)
    val isUpdatesScreenActive: StateFlow<Boolean> = _isUpdatesScreenActive.asStateFlow()

    fun setUpdatesScreenActive(active: Boolean) {
        _isUpdatesScreenActive.value = active
    }

    private val _activeAptTask = MutableStateFlow<ActiveAptTask?>(null)
    val activeAptTask: StateFlow<ActiveAptTask?> = _activeAptTask.asStateFlow()

    private val _activeAptLogLine = MutableStateFlow<ClusterLogEntry?>(null)
    val activeAptLogLine: StateFlow<ClusterLogEntry?> = _activeAptLogLine.asStateFlow()

    val latestLog: StateFlow<ClusterLogEntry?> = combine(
        _clusterLogCache,
        _activeAptTask,
        _activeAptLogLine,
        _isUpdatesScreenActive,
    ) { entries, aptTask, aptLine, updatesActive ->
        val sessionUser = sessionStore.session.value?.username
            ?: sessionStore.session.value?.config?.username
        if (!updatesActive && aptTask != null && aptLine != null) {
            aptLine
        } else {
            filterLatestLogForStrip(
                entries = entries,
                recentRegistry = recentActionRegistry,
                sessionUser = sessionUser,
                isUpdatesScreenActive = updatesActive,
            )
        }
    }.stateIn(
        scope = repoScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun setActiveAptTask(node: String, upid: String, type: String) {
        _activeAptTask.value = ActiveAptTask(node, upid, type)
    }

    fun clearActiveAptTask(upid: String? = null) {
        if (upid == null || _activeAptTask.value?.upid == upid) {
            _activeAptTask.value = null
            _activeAptLogLine.value = null
        }
    }

    fun updateTaskLogLine(node: String, upid: String, type: String, line: String) {
        if (line.isNotBlank()) {
            val entry = ClusterLogEntry(
                id = "task-$upid-${System.currentTimeMillis()}",
                time = System.currentTimeMillis() / 1000,
                node = node,
                tag = if (type == "aptupgrade") "apt-upgrade" else "apt-update",
                msg = line,
                pri = 6,
            )
            _activeAptLogLine.value = entry
        }
    }

    private val guestConfigCache = ConcurrentHashMap<String, GuestConfigCacheEntry>()

    private data class GuestConfigCacheEntry(
        val ostype: String?,
        val onboot: Int?,
        val fetchedAtEpochMs: Long
    )

    /**
     * @param saveCredentials store password/token on the profile (user toggle)
     * @param profileId update an existing saved profile when re-using one
     * @param enableAutoConnect if non-null, updates global auto-connect pref
     */
    suspend fun login(
        config: ServerConfig,
        saveCredentials: Boolean = true,
        profileId: String? = null,
        enableAutoConnect: Boolean? = null,
        label: String = "",
        forceNewProfile: Boolean = false,
        silent: Boolean = false,
    ): LoginOutcome {
        return try {
            clientFactory.clear()
            val api = clientFactory.apiFor(config)
            var ticket: String? = null
            var csrf: String? = null
            var username: String? = null

            when (config.authMode) {
                AuthMode.PASSWORD -> {
                    val user = normalizeUsername(config.username, config.realm)
                    val resp = try {
                        api.createTicket(user, config.password)
                    } catch (e: retrofit2.HttpException) {
                        val errorBody = try {
                            e.response()?.errorBody()?.string()
                        } catch (_: Exception) {
                            null
                        }
                        if (e.code() == 401) {
                            val partialTicket = extractTfaTicket(errorBody)
                            if (partialTicket != null) {
                                return LoginOutcome.NeedsTfa(
                                    partialTicket = partialTicket,
                                    config = config,
                                    saveCredentials = saveCredentials,
                                    profileId = profileId,
                                    enableAutoConnect = enableAutoConnect,
                                    label = label,
                                    forceNewProfile = forceNewProfile,
                                )
                            }
                        }
                        throw PveHttpException(e.code(), errorBody, e.message(), e)
                    }
                    val data = resp.data
                        ?: return LoginOutcome.Failed(PveException("Login failed: empty ticket response"))
                    val rawTicket = data.ticket
                        ?: return LoginOutcome.Failed(PveException("Login failed: no ticket"))
                    if ((data.needTfa ?: 0) != 0 || rawTicket.startsWith("PVE:tfa!") || rawTicket.contains("TFA:") || rawTicket.contains("TFA-PARTIAL") || data.cap?.containsKey("NeedTFA") == true) {
                        return LoginOutcome.NeedsTfa(
                            partialTicket = rawTicket,
                            config = config,
                            saveCredentials = saveCredentials,
                            profileId = profileId,
                            enableAutoConnect = enableAutoConnect,
                            label = label,
                            forceNewProfile = forceNewProfile,
                        )
                    }
                    ticket = rawTicket
                    csrf = data.csrfPreventionToken
                    username = data.username ?: user
                }
                AuthMode.API_TOKEN -> {
                    if (config.apiToken.isBlank()) {
                        return LoginOutcome.Failed(PveException("API token is empty"))
                    }
                    username = config.apiToken.substringBefore('!').ifBlank { null }
                }
            }

            // Persist full secrets only in encrypted profile store (if user opted in).
            // Live session must not keep the password in memory after ticket mint.
            sessionStore.saveProfileFromLogin(
                config = config,
                saveCredentials = saveCredentials,
                profileId = profileId,
                label = label,
                forceNewProfile = forceNewProfile,
                version = null,
            )

            val sessionConfig = config.withoutEphemeralSecrets()
            val partial = SessionState(
                config = sessionConfig,
                ticket = ticket,
                csrf = csrf,
                username = username,
            )
            sessionStore.setSession(partial)

            val version = api.version().data
            val full = partial.copy(version = version)
            sessionStore.setSession(full)

            if (config.trustSelfSigned) {
                clientFactory.getCapturedFingerprint(config.host)?.let { fp ->
                    sessionStore.saveCertPin(config.host, fp)
                }
            }

            commitLoginSideEffects(config, version, enableAutoConnect)

            LoginOutcome.Success(full)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!silent) {
                sessionStore.clearSession()
                clientFactory.clear()
            }
            LoginOutcome.Failed(mapError(e))
        }
    }

    /**
     * Completes two-factor login by re-posting /access/ticket with the TFA challenge
     * and the OTP (password prefixed with totp:). The partial ticket is NEVER persisted.
     */
    suspend fun completeTfa(
        config: ServerConfig,
        partialTicket: String,
        otp: String,
        saveCredentials: Boolean = true,
        profileId: String? = null,
        enableAutoConnect: Boolean? = null,
        label: String = "",
        forceNewProfile: Boolean = false,
    ): Result<SessionState> {
        return try {
            clientFactory.clear()
            val api = clientFactory.apiFor(config)
            val resp = api.createTicketTfa(
                username = normalizeUsername(config.username, config.realm),
                password = "totp:${otp.trim()}",
                tfaChallenge = partialTicket,
            )
            val data = resp.data
                ?: return Result.failure(PveException("TFA verification failed: empty response"))
            val ticket = data.ticket
                ?: return Result.failure(PveException("TFA verification failed: no ticket"))
            val csrf = data.csrfPreventionToken
            val user = data.username ?: normalizeUsername(config.username, config.realm)

            // Persist credentials only now that login is fully verified (never save partial ticket)
            sessionStore.saveProfileFromLogin(
                config = config,
                saveCredentials = saveCredentials,
                profileId = profileId,
                label = label,
                forceNewProfile = forceNewProfile,
                version = null,
            )

            val sessionConfig = config.withoutEphemeralSecrets()
            val partial = SessionState(
                config = sessionConfig,
                ticket = ticket,
                csrf = csrf,
                username = user,
            )
            sessionStore.setSession(partial)

            val version = api.version().data
            val full = partial.copy(version = version)
            sessionStore.setSession(full)

            if (config.trustSelfSigned) {
                clientFactory.getCapturedFingerprint(config.host)?.let { fp ->
                    sessionStore.saveCertPin(config.host, fp)
                }
            }

            commitLoginSideEffects(config, version, enableAutoConnect)

            Result.success(full)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            sessionStore.clearSession()
            clientFactory.clear()
            Result.failure(mapError(e))
        }
    }

    /**
     * Post-login bookkeeping shared by login() and completeTfa() so the demo
     * skip cannot drift between the two paths. Demo sessions touch no real
     * profile and set no auto-connect preference.
     */
    private fun commitLoginSideEffects(
        config: ServerConfig,
        version: VersionInfo?,
        enableAutoConnect: Boolean?,
    ) {
        if (config.host.equals("demo", ignoreCase = true)) return
        sessionStore.lastProfileId()?.let { id ->
            sessionStore.touchProfile(id, version = version?.display)
        }
        enableAutoConnect?.let { sessionStore.setAutoConnect(it) }
    }

    private fun extractTfaTicket(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = AppJson.parseToJsonElement(errorBody).jsonObject
            val dataElem = json["data"]
            if (dataElem is JsonObject) {
                val ticket = dataElem["ticket"]?.jsonPrimitive?.contentOrNull
                if (!ticket.isNullOrBlank()) return ticket
            } else if (dataElem is JsonPrimitive && dataElem.isString) {
                val str = dataElem.content
                if (str.isNotBlank()) return str
            }
            val ticket = json["ticket"]?.jsonPrimitive?.contentOrNull
            if (!ticket.isNullOrBlank()) return ticket
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Password is only needed to mint a ticket; drop it from the live session.
     * API tokens must remain — every request authenticates with the token.
     */
    private fun ServerConfig.withoutEphemeralSecrets(): ServerConfig = when (authMode) {
        AuthMode.PASSWORD -> copy(password = "")
        AuthMode.API_TOKEN -> this
    }

    suspend fun loginWithProfile(profileId: String, silent: Boolean = false): LoginOutcome {
        val profile = sessionStore.getProfile(profileId)
            ?: return LoginOutcome.Failed(PveException("Profile not found"))
        if (!profile.hasSavedSecret) {
            return LoginOutcome.Failed(PveException("No saved credentials for this profile"))
        }
        return login(
            config = profile.toServerConfig(includeSecrets = true),
            saveCredentials = profile.saveCredentials,
            profileId = profile.id,
            silent = silent,
        )
    }

    suspend fun probeProfile(profile: SavedProfile): Result<ServerProbe> {
        val config = profile.toServerConfig(includeSecrets = true)
        if (!profile.hasSavedSecret && !config.host.equals("demo", ignoreCase = true)) {
            return Result.failure(PveException("No saved credentials"))
        }

        val probeApi = clientFactory.apiForProbe(config)
        val api = probeApi.api

        return try {
            if (config.authMode == AuthMode.PASSWORD) {
                val user = normalizeUsername(config.username, config.realm)
                val resp = api.createTicket(user, config.password)
                val ticket = resp.data?.ticket ?: throw PveException("Login failed")
                val csrf = resp.data?.csrfPreventionToken
                probeApi.probeAuth.set(ProbeAuth(ticket, csrf))
            }

            val version = api.version().data?.display
            val nodes = api.nodes().data.orEmpty()
            if (nodes.isEmpty()) throw PveException("No nodes found")

            val all = nodes.flatMap { n ->
                val nodeName = n.node ?: return@flatMap emptyList()
                val qemu = api.nodeQemu(nodeName).data.orEmpty()
                val lxc = api.nodeLxc(nodeName).data.orEmpty()
                qemu + lxc
            }

            val running = all.count { it.status == "running" || it.status == "online" }
            val stopped = all.count { it.status == "stopped" || it.status == "offline" }
            val guestPairs = all.map { (it.name ?: it.vmid?.toString() ?: "unknown") to (it.status ?: "unknown") }

            Result.success(
                ServerProbe(
                    host = config.displayHost,
                    version = version,
                    running = running,
                    stopped = stopped,
                    guests = guestPairs
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            probeApi.probeAuth.set(null)
        }
    }

    suspend fun testConnection(host: String, port: Int = 8006, timeoutMs: Long = 8000L): ConnectionTestResult =
        localNet.testConnection(host, port, timeoutMs)

    /**
     * Tests a connection using stored credentials if available.
     * Maps to ONLINE · PVE x.y.z · Nms on success.
     */
    suspend fun testProfileConnection(profile: SavedProfile): ConnectionTestResult {
        if (profile.host.equals("demo", ignoreCase = true)) {
            return ConnectionTestResult(online = true, version = "8.3.0", latencyMs = 12L)
        }
        
        val start = SystemClock.elapsedRealtime()
        val config = profile.toServerConfig(includeSecrets = true)
        val probeApi = clientFactory.apiForProbe(config)
        val api = probeApi.api
        return try {
            
            // If it's PASSWORD mode, we might need a fresh ticket. 
            // apiCall normally handles this, but here we want to test specifically with this profile.
            val version = if (config.authMode == AuthMode.PASSWORD) {
                val user = normalizeUsername(config.username, config.realm)
                val ticketResp = api.createTicket(user, config.password)
                val ticket = ticketResp.data?.ticket ?: throw PveException("Authentication failed")
                val csrf = ticketResp.data?.csrfPreventionToken
                
                // The probe client's own slot carries the ticket; live traffic never sees it.
                probeApi.probeAuth.set(ProbeAuth(ticket, csrf))
                try {
                    api.version().data
                } finally {
                    probeApi.probeAuth.set(null)
                }
            } else {
                // API Token mode - just call version()
                api.version().data
            }

            val latency = SystemClock.elapsedRealtime() - start
            ConnectionTestResult(
                online = true,
                version = version?.display,
                latencyMs = latency
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val latency = SystemClock.elapsedRealtime() - start
            val msg = mapError(e).message ?: "Connection failed"
            ConnectionTestResult(online = false, error = msg, latencyMs = latency)
        }
    }

    suspend fun tryAutoConnect(): Result<SessionState> {
        if (!sessionStore.autoConnect.value) {
            return Result.failure(PveException("Auto-connect disabled"))
        }
        val profile = sessionStore.lastProfileId()?.let { sessionStore.getProfile(it) }
            ?: sessionStore.getLastProfile()
            ?: return Result.failure(PveException("No saved profile"))
        if (!profile.hasSavedSecret) {
            return Result.failure(PveException("No saved credentials"))
        }
        return when (val outcome = loginWithProfile(profile.id)) {
            is LoginOutcome.Success -> Result.success(outcome.session)
            is LoginOutcome.NeedsTfa -> Result.failure(PveException("Two-factor authentication required"))
            is LoginOutcome.Failed -> Result.failure(outcome.error)
        }
    }

    fun logout(rememberAsPrevious: Boolean = true) {
        sessionStore.clearSession(rememberAsPrevious = rememberAsPrevious)
        clientFactory.clear()
        clearWebCookies()
        guestConfigCache.clear()
    }

    /** Drop console / noVNC PVEAuthCookie so a later user on this device cannot reuse it. */
    private fun clearWebCookies() {
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
    }

    suspend fun refreshVersion(): Result<VersionInfo> {
        return apiCall { api ->
            val v = api.version().data ?: throw PveException("No version data")
            sessionStore.updateVersion(v)
            v
        }
    }

    /**
     * Detect real multi-node cluster vs standalone.
     * Standalone still answers on `/cluster/status` with a single local node
     * and **no** `type=cluster` entry — do not label that as a cluster in UI.
     */
    suspend fun siteInfo(): Result<SiteInfo> {
        return apiCall { api ->
            val status = runCatching { api.clusterStatus().data.orEmpty() }.getOrDefault(emptyList())
            val nodes = status.filter { (it["type"] as? String) == "node" }
            val clusterEntry = status.firstOrNull { (it["type"] as? String) == "cluster" }
            val localNode = nodes.firstOrNull { it["local"] == 1 || it["local"] == 1.0 || it["local"] == true }
                ?: nodes.firstOrNull()
            val nodeName = (localNode?.get("name") as? String)
                ?: runCatching { api.nodes().data.orEmpty().firstOrNull()?.node }.getOrNull()
            val nodeCount = nodes.size.coerceAtLeast(
                runCatching { api.nodes().data.orEmpty().size }.getOrDefault(1),
            )
            val clusterName = clusterEntry?.get("name") as? String
            val isCluster = clusterEntry != null || nodeCount > 1
            SiteInfo(
                isCluster = isCluster,
                clusterName = clusterName,
                nodeName = nodeName,
                nodeCount = nodeCount,
            )
        }
    }

    /**
     * Build a rich resource list from per-node endpoints.
     * Note: `/cluster/resources` is an API path even on standalone — prefer node APIs.
     */
    suspend fun listResources(type: String? = null): Result<List<ClusterResource>> {
        return apiCall { api ->
            val nodeNames = discoverNodeNames(api)
            if (nodeNames.isEmpty()) {
                return@apiCall api.clusterResources(type).data.orEmpty()
            }

            val out = coroutineScope {
                nodeNames.map { nodeName ->
                    async {
                        val nodeRes = loadNodeResource(api, nodeName)
                        val qemu = loadGuests(api, nodeName, "qemu")
                        val lxc = loadGuests(api, nodeName, "lxc")
                        val storage = loadStorage(api, nodeName)
                        listOf(nodeRes) + qemu + lxc + storage
                    }
                }.flatMap { it.await() }
            }

            when (type) {
                null -> out
                "vm" -> out.filter { it.type == "qemu" || it.type == "lxc" }
                else -> out.filter { it.type == type }
            }
        }
    }

    private suspend fun discoverNodeNames(api: com.pxmx.app.data.api.ProxmoxApi): List<String> {
        val fromNodes = api.nodes().data.orEmpty().mapNotNull { it.node }.filter { it.isNotBlank() }
        if (fromNodes.isNotEmpty()) return fromNodes.distinct()
        return api.clusterResources("node").data.orEmpty()
            .mapNotNull { it.node }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private suspend fun loadNodeResource(
        api: com.pxmx.app.data.api.ProxmoxApi,
        nodeName: String,
    ): ClusterResource {
        val status = try {
            api.nodeStatus(nodeName).data
        } catch (_: Exception) {
            null
        }
        return ClusterResource(
            id = "node/$nodeName",
            type = "node",
            node = nodeName,
            name = nodeName,
            status = if (status != null) "online" else "unknown",
            uptime = status?.uptime,
            cpu = status?.cpu,
            mem = status?.memory?.used,
            maxmem = status?.memory?.total,
            disk = status?.rootfs?.used,
            maxdisk = status?.rootfs?.total,
        )
    }

    private suspend fun loadGuests(
        api: com.pxmx.app.data.api.ProxmoxApi,
        nodeName: String,
        guestType: String,
    ): List<ClusterResource> {
        val rows = try {
            when (guestType) {
                "qemu" -> api.nodeQemu(nodeName).data.orEmpty()
                "lxc" -> api.nodeLxc(nodeName).data.orEmpty()
                else -> emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        // Enrich with ostype from config (list endpoint omits it). Small node counts only.
        return coroutineScope {
            rows.map { g ->
                async {
                    val vmid = g.vmid
                    val cacheKey = "$nodeName/$guestType/$vmid"
                    val now = System.currentTimeMillis()
                    val cached = if (vmid != null) guestConfigCache[cacheKey] else null

                    val extras = if (vmid != null) {
                        if (cached != null && now - cached.fetchedAtEpochMs < 60_000) {
                            cached.ostype to cached.onboot
                        } else {
                            try {
                                val cfg = api.guestConfig(nodeName, guestType, vmid).data.orEmpty()
                                val ostype = cfg["ostype"]?.let { pveScalar(it) }
                                val onboot = cfg["onboot"]?.let { pveScalar(it).toIntOrNull() }
                                guestConfigCache[cacheKey] = GuestConfigCacheEntry(ostype, onboot, now)
                                ostype to onboot
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                cached?.ostype to cached?.onboot
                            }
                        }
                    } else {
                        null
                    }
                    g.copy(
                        id = g.id ?: "$guestType/${g.vmid}",
                        type = guestType,
                        node = nodeName,
                        maxcpu = g.maxcpu ?: g.cpus,
                        ostype = extras?.first ?: g.ostype,
                        onboot = extras?.second ?: g.onboot,
                    )
                }
            }.map { it.await() }
        }
    }

    private fun pveScalar(value: Any): String = when (value) {
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Float -> if (value % 1f == 0f) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    private suspend fun loadStorage(
        api: com.pxmx.app.data.api.ProxmoxApi,
        nodeName: String,
    ): List<ClusterResource> {
        val rows = try {
            api.nodeStorage(nodeName).data.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        return rows.map { s ->
            val name = s.storage ?: "storage"
            ClusterResource(
                id = "storage/$nodeName/$name",
                type = "storage",
                node = nodeName,
                name = name,
                storage = name,
                plugintype = s.type,
                content = s.content,
                shared = s.shared,
                active = s.active,
                enabled = s.enabled,
                status = when {
                    s.active == 1 -> "available"
                    s.enabled == 0 -> "disabled"
                    else -> "unknown"
                },
                disk = s.used,
                maxdisk = s.total,
            )
        }
    }

    suspend fun nodeStatus(node: String): Result<NodeStatus> {
        return apiCall { api ->
            api.nodeStatus(node).data ?: throw PveException("No node status")
        }
    }

    suspend fun guestStatus(node: String, guestType: GuestType, vmid: Long): Result<GuestStatus> =
        apiCall { api ->
            api.guestStatus(node, guestType.path, vmid).data
                ?: throw PveException("No guest status")
        }

    suspend fun guestAction(
        node: String,
        guestType: GuestType,
        vmid: Long,
        action: GuestAction,
    ): Result<String> = apiCall { api ->
        val taskType = when (guestType) {
            GuestType.QEMU -> when (action) {
                GuestAction.START -> "qmstart"
                GuestAction.STOP -> "qmstop"
                GuestAction.SHUTDOWN -> "qmshutdown"
                GuestAction.REBOOT -> "qmreboot"
                GuestAction.RESET -> "qmreset"
                GuestAction.SUSPEND -> "qmsuspend"
                GuestAction.RESUME -> "qmresume"
            }
            GuestType.LXC -> when (action) {
                GuestAction.START -> "pctstart"
                GuestAction.STOP -> "pctstop"
                GuestAction.SHUTDOWN -> "pctshutdown"
                GuestAction.REBOOT -> "pctreboot"
                GuestAction.RESET -> "pctstop"
                GuestAction.SUSPEND -> "pctsuspend"
                GuestAction.RESUME -> "pctresume"
            }
            GuestType.NODE -> "node"
        }
        recentActionRegistry.record(taskType, vmid)

        api.guestAction(node, guestType.path, vmid, action.apiName).data
            ?: throw PveException("Action returned no UPID")
    }

    suspend fun deployFromTemplate(
        source: ClusterResource,
        newId: Long,
        name: String,
    ): Result<String> = apiCall { api ->
        val node = source.node ?: throw PveException("Template has no node")
        val type = source.type ?: throw PveException("Template has no type")
        val vmid = source.vmid ?: throw PveException("Template has no VMID")
        val isQemu = type == "qemu"
        api.cloneGuest(
            node = node,
            type = type,
            vmid = vmid,
            newid = newId,
            name = if (isQemu) name else null,
            hostname = if (!isQemu) name else null
        ).data ?: throw PveException("Clone returned no UPID")
    }

    /**
     * Full guest “hooks”: status + config (parsed) + snapshots + backups + host USB map.
     */
    suspend fun loadGuestBundle(
        node: String,
        guestType: GuestType,
        vmid: Long,
    ): Result<GuestBundle> {
        return apiCall { api ->
            coroutineScope {
                val statusDef = async {
                    runCatching { api.guestStatus(node, guestType.path, vmid).data }
                        .getOrNull()
                }
                val configDef = async {
                    runCatching { api.guestConfig(node, guestType.path, vmid).data.orEmpty() }
                        .getOrDefault(emptyMap())
                }
                val snapsDef = async {
                    runCatching { api.guestSnapshots(node, guestType.path, vmid).data.orEmpty() }
                        .getOrDefault(emptyList())
                }
                val usbDef = async {
                    runCatching { api.nodeUsb(node).data.orEmpty() }
                        .getOrDefault(emptyList())
                }
                val storageDef = async {
                    runCatching { api.nodeStorage(node).data.orEmpty() }
                        .getOrDefault(emptyList())
                }

                val status = statusDef.await()
                val rawConfig = configDef.await()
                val hostUsbs = usbDef.await()
                val parsed = GuestConfigParser.parse(rawConfig, hostUsbs)
                val storages = storageDef.await()
                val backups = loadBackupsForVmid(api, node, vmid, storages)

                GuestBundle(
                    status = status,
                    config = parsed,
                    snapshots = snapsDef.await()
                        .sortedWith(compareBy<SnapshotInfo> { if (it.isCurrent) 0 else 1 }
                            .thenByDescending { it.snaptime ?: 0L }),
                    backups = backups.sortedByDescending { it.ctime ?: 0L },
                    hostUsbs = hostUsbs,
                    backupStorages = storages
                        .filter { (it.content ?: "").contains("backup") }
                        .mapNotNull { it.storage },
                )
            }
        }
    }

    private suspend fun loadBackupsForVmid(
        api: com.pxmx.app.data.api.ProxmoxApi,
        node: String,
        vmid: Long,
        storages: List<NodeStorageEntry>,
    ): List<BackupVolume> {
        val out = mutableListOf<BackupVolume>()
        for (st in storages) {
            val name = st.storage ?: continue
            if (!(st.content ?: "").contains("backup")) continue
            val items = runCatching {
                api.storageContent(node, name, content = "backup", vmid = vmid).data.orEmpty()
            }.getOrElse {
                runCatching {
                    api.storageContent(node, name, content = "backup").data.orEmpty()
                        .filter { it.vmid == vmid || it.volid?.contains("-$vmid-") == true }
                }.getOrDefault(emptyList())
            }
            out += items
                .filter { it.vmid == null || it.vmid == vmid || it.volid?.contains("-$vmid-") == true }
                .map { it.toBackupVolume() }
        }
        return out.distinctBy { it.volid }
    }

    private fun StorageContentItem.toBackupVolume() = BackupVolume(
        volid = volid,
        content = content,
        format = format,
        size = size,
        ctime = ctime,
        vmid = vmid,
        notes = notes,
        subtype = subtype,
    )

    /** Attach host USB (vid:pid) to next free usbN slot. */
    suspend fun attachUsb(
        node: String,
        guestType: GuestType,
        vmid: Long,
        hostId: String,
        usb3: Boolean = true,
    ): Result<String> = apiCall { api ->
        if (guestType != GuestType.QEMU) throw PveException("USB passthrough is QEMU-only")
        val cfg = api.guestConfig(node, guestType.path, vmid).data.orEmpty()
        val used = cfg.keys.filter { it.matches(Regex("^usb\\d+$")) }.toSet()
        val slot = (0..15).firstOrNull { "usb$it" !in used }
            ?: throw PveException("No free USB slots (usb0–usb15)")
        val value = buildString {
            append("host=").append(hostId.lowercase())
            if (usb3) append(",usb3=1")
        }
        val resp = api.updateGuestConfig(
            node, guestType.path, vmid,
            mapOf("usb$slot" to value),
        )
        resp.data ?: "OK"
    }

    /** Detach guest usbN (e.g. usb0). */
    suspend fun detachUsb(
        node: String,
        guestType: GuestType,
        vmid: Long,
        usbKey: String,
    ): Result<String> = apiCall { api ->
        if (!usbKey.matches(Regex("^usb\\d+$"))) {
            throw PveException("Invalid USB key: $usbKey")
        }
        val resp = api.updateGuestConfig(
            node, guestType.path, vmid,
            mapOf("delete" to usbKey),
        )
        resp.data ?: "OK"
    }

    suspend fun listHostUsb(node: String): Result<List<HostUsbDevice>> = apiCall { api ->
        api.nodeUsb(node).data.orEmpty()
    }

    suspend fun storageDetail(
        node: String,
        storage: String,
        contentFilter: String? = null,
    ): Result<StorageDetail> {
        return apiCall { api ->
            val status = runCatching { api.storageStatus(node, storage).data }.getOrNull()
                ?: StorageStatus(storage = storage)
            val content = runCatching {
                api.storageContent(node, storage, content = contentFilter).data.orEmpty()
            }.getOrDefault(emptyList())
            StorageDetail(
                node = node,
                storage = storage,
                status = status.copy(storage = status.storage ?: storage),
                content = content.sortedWith(
                    compareBy<StorageContentItem> { it.content ?: "" }
                        .thenByDescending { it.ctime ?: 0L }
                        .thenBy { it.volid ?: "" },
                ),
            )
        }
    }

    suspend fun deleteStorageVolume(node: String, volid: String): Result<String> =
        deleteBackup(node, volid)

    suspend fun backupToDevice(
        node: String,
        type: String,
        vmid: Long,
        storage: String,
        onProgress: (String) -> Unit
    ): Result<String> {
        val profileId = sessionStore.lastProfileId()
        val profile = profileId?.let { sessionStore.getProfile(it) } ?: return Result.failure(PveException("No profile found"))
        val config = profile.toServerConfig(includeSecrets = true)
        
        return try {
            onProgress("Backing up on server...")
            val upid = createBackup(node, vmid, storage).getOrThrow()
            
            // Poll for completion (max 10 mins)
            val startTime = System.currentTimeMillis()
            var finished = false
            while (System.currentTimeMillis() - startTime < 600_000) {
                val status = taskStatus(node, upid).getOrThrow()
                if (!status.isRunning) {
                    if (!status.isOk) throw PveException("Backup task failed: ${status.exitstatus}")
                    finished = true
                    break
                }
                delay(2000)
            }
            if (!finished) throw PveException("Backup timed out")

            onProgress("Locating backup volume...")
            // Find newest volume for this VM
            val resp = apiCall { it.storageContent(node, storage, content = "backup", vmid = vmid) }.getOrThrow()
            val items = resp.data.orEmpty()
            val newest = items.maxByOrNull { it.ctime ?: 0L }
                ?: throw PveException("Could not find resulting backup volume")

            val volid = newest.volid ?: throw PveException("Volume missing ID")
            val volumeName = volid.substringAfter(':')

            // Fetch single volume metadata to get the 'path'
            val detailResp = apiCall { it.storageVolume(node, storage, volumeName) }.getOrThrow()
            val remotePath = detailResp.data?.path
                ?: throw PveException("Server did not return file path. SFTP download requires the full path.")
            
            val rawFilename = volid.substringAfterLast('/')
            val filename = sanitizeBackupFilename(rawFilename)

            onProgress("Starting SFTP download...")
            val sftp = SftpDownloader(
                getStoredFingerprint = { host -> sessionStore.getHostKey(host) },
                storeFingerprint = { host, key -> sessionStore.saveHostKey(host, key) }
            )

            val downloadResult = runCatching {
                saveToDownloadsToStream(filename) { outputStream ->
                    sftp.download(
                        host = config.host,
                        port = 22, // Default SSH port
                        username = config.username,
                        password = config.password,
                        remotePath = remotePath,
                        localSink = outputStream,
                        onProgress = { downloaded, total ->
                            val pct = if (total > 0) " (${(downloaded * 100 / total)}%)" else ""
                            onProgress("Downloading $filename$pct...")
                        }
                    )
                }
            }
            
            downloadResult.getOrElse { e ->
                throw PveException("Backup created on server but download failed: ${e.message}. The backup remains on the server.", e)
            }.map { filename }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun saveToDownloadsToStream(filename: String, block: suspend (OutputStream) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outputStream: OutputStream?
            val uri: Uri?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Proxmox")
                }
                uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("Failed to create MediaStore entry")
                outputStream = context.contentResolver.openOutputStream(uri)
            } else {
                // Public Downloads needs WRITE_EXTERNAL_STORAGE before Android 10,
                // which this app does not request. Keep the save path honest.
                throw PveException("Saving backups to Downloads requires Android 10 or newer")
            }

            try {
                outputStream.use { out ->
                    if (out == null) throw IOException("Failed to open output stream")
                    block(out)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                // Cleanup partial file on failure if possible
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                    context.contentResolver.delete(uri, null, null)
                }
                throw e
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Toggle start-at-boot on a guest (config write). */
    suspend fun setGuestOnboot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        enabled: Boolean,
    ): Result<String> = apiCall { api ->
        val taskType = if (guestType == GuestType.QEMU) "qmset" else "pctset"
        recentActionRegistry.record(taskType, vmid)

        val res = api.updateGuestConfig(
            node,
            guestType.path,
            vmid,
            mapOf("onboot" to if (enabled) "1" else "0"),
        ).data ?: "OK"

        // Update cache so UI doesn't flip back during next poll
        val cacheKey = "$node/${guestType.path}/$vmid"
        guestConfigCache[cacheKey]?.let {
            guestConfigCache[cacheKey] = it.copy(
                onboot = if (enabled) 1 else 0,
                fetchedAtEpochMs = System.currentTimeMillis()
            )
        }
        res
    }

    /**
     * Open the same console the Proxmox web UI uses (noVNC for QEMU / LXC, xterm.js for node).
     * Requires an active password/ticket session (API tokens often lack console rights).
     */
    suspend fun openConsole(
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
        cmd: String? = null,
    ): Result<ConsoleSession> = apiCall { api ->
        val session = sessionStore.session.value
            ?: throw PveException("Not connected")
        val isDemo = session.config.host.equals("demo", ignoreCase = true)
        val authCookie = session.ticket
            ?: if (isDemo) "DEMO_TICKET"
            else throw PveException(
                "Console needs a ticket login (password). API-token sessions usually cannot open noVNC.",
            )

        val proxy = when (guestType) {
            GuestType.QEMU -> api.qemuVncProxy(node, vmid).data
            GuestType.LXC -> api.lxcVncProxy(node, vmid).data
            GuestType.NODE -> api.nodeTermProxy(node, cmd).data
        } ?: throw PveException("Console proxy returned empty data")

        val port = proxy.port ?: throw PveException("Console proxy missing port")
        val vncticket = proxy.ticket ?: throw PveException("Console proxy missing ticket")

        val cfg = session.config
        val hostPort = cfg.displayHost // host:port
        val cookieHostUrl = "https://$hostPort"
        val typePath = guestType.path
        val ticketEnc = URLEncoder.encode(vncticket, StandardCharsets.UTF_8.toString())
        val rawPath = if (guestType == GuestType.NODE) {
            "api2/json/nodes/$node/vncwebsocket?port=$port&vncticket=$ticketEnc"
        } else {
            "api2/json/nodes/$node/$typePath/$vmid/vncwebsocket?port=$port&vncticket=$ticketEnc"
        }
        val pathEnc = URLEncoder.encode(rawPath, StandardCharsets.UTF_8.toString())
        val consoleKind = if (guestType == GuestType.NODE) {
            if (cmd == "upgrade") "upgrade" else if (cmd == "login") "login" else "shell"
        } else if (guestType == GuestType.QEMU) "kvm" else "lxc"
        val uiParam = if (guestType == GuestType.NODE) "xtermjs=1" else "novnc=1"
        // scale = fit remote desktop to browser viewport (better on phones)
        val pageUrl = if (isDemo) {
            DemoShell.generateHtml(node, guestType, vmid, name)
        } else {
            "$cookieHostUrl/?console=$consoleKind&$uiParam&vmid=$vmid&node=$node&resize=scale&path=$pathEnc"
        }

        ConsoleSession(
            pageUrl = pageUrl,
            cookieHostUrl = cookieHostUrl,
            pveAuthCookie = authCookie,
            guestType = guestType,
            node = node,
            vmid = vmid,
            name = name,
        )
    }

    suspend fun createSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
        description: String? = null,
        includeRam: Boolean = false,
    ): Result<String> = apiCall { api ->
        api.createSnapshot(
            node = node,
            type = guestType.path,
            vmid = vmid,
            snapname = name,
            description = description,
            vmstate = if (includeRam && guestType == GuestType.QEMU) 1 else null,
        ).data ?: throw PveException("Snapshot returned no UPID")
    }

    suspend fun deleteSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        snap: String,
    ): Result<String> = apiCall { api ->
        api.deleteSnapshot(node, guestType.path, vmid, snap).data
            ?: "OK"
    }

    suspend fun rollbackSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        snap: String,
    ): Result<String> = apiCall { api ->
        api.rollbackSnapshot(node, guestType.path, vmid, snap).data
            ?: throw PveException("Rollback returned no UPID")
    }

    suspend fun createBackup(
        node: String,
        vmid: Long,
        storage: String,
        mode: String = "snapshot",
        compress: String = "zstd",
    ): Result<String> = apiCall { api ->
        api.createBackup(
            node = node,
            vmid = vmid,
            storage = storage,
            mode = mode,
            compress = compress,
            notesTemplate = "{{guestname}}",
        ).data ?: throw PveException("Backup returned no UPID")
    }

    suspend fun deleteBackup(
        node: String,
        volid: String,
    ): Result<String> = apiCall { api ->
        // volid is storage:path — DELETE content needs storage + volume path
        val storage = volid.substringBefore(':')
        val volume = volid // API often wants full volid encoded
        api.deleteStorageContent(node, storage, volume).data ?: "OK"
    }

    suspend fun taskStatus(node: String, upid: String): Result<TaskStatus> = apiCall { api ->
        api.taskStatus(node, upid).data ?: throw PveException("No task status")
    }

    suspend fun clusterTasks(): Result<List<Map<String, Any>>> = apiCall { api ->
        api.clusterTasks().data.orEmpty()
    }

    suspend fun logPoll(max: Int = 10): Result<List<ClusterLogEntry>> = apiCall { api ->
        val activeTask = _activeAptTask.value
        if (activeTask != null) {
            val lines = runCatching {
                api.taskLog(activeTask.node, activeTask.upid, limit = 5).data.orEmpty()
            }.getOrDefault(emptyList())
            val lastLine = lines.lastOrNull()?.get("t")?.toString()
            if (!lastLine.isNullOrBlank()) {
                updateTaskLogLine(activeTask.node, activeTask.upid, activeTask.type, lastLine)
            }
        }
        val entries = api.clusterLog(max = max).data.orEmpty()
        _clusterLogCache.value = entries
        entries
    }

    suspend fun logHistory(max: Int = 200): Result<List<ClusterLogEntry>> = apiCall { api ->
        val entries = api.clusterLog(max = max).data.orEmpty()
        _clusterLogCache.value = entries
        entries
    }

    /**
     * Poll task until finished or [timeoutMs] elapses.
     */
    suspend fun awaitTask(
        node: String,
        upid: String,
        timeoutMs: Long = 60_000,
        intervalMs: Long = 1_000,
    ): Result<TaskStatus> {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val result = taskStatus(node, upid)
            val status = result.getOrElse { return Result.failure(it) }
            if (!status.isRunning) return Result.success(status)
            delay(intervalMs)
        }
        return Result.failure(PveException("Task timed out: $upid"))
    }

    /** All node names currently known to the session. */
    suspend fun listNodeNames(): Result<List<String>> = apiCall { api ->
        discoverNodeNames(api)
    }

    suspend fun listNodeStorageNames(node: String): Result<List<String>> = apiCall { api ->
        api.nodeStorage(node).data.orEmpty().mapNotNull { it.storage }
    }

    /**
     * Network interfaces for every node (read-only overview).
     * iface maps vary by type (bridge, bond, eth, vlan…).
     */
    suspend fun listClusterNetwork(): Result<List<NodeNetworkSnapshot>> = apiCall { api ->
        val nodes = discoverNodeNames(api)
        nodes.map { node ->
            val ifaces = runCatching { api.nodeNetwork(node).data.orEmpty() }
                .getOrDefault(emptyList())
                .map { raw -> NetworkIface.fromMap(raw) }
                .sortedWith(
                    compareBy<NetworkIface> { it.type.orEmpty() }
                        .thenBy { it.iface.orEmpty() },
                )
            NodeNetworkSnapshot(node = node, interfaces = ifaces)
        }
    }

    /** Optional SDN zones; empty if SDN is not configured. */
    suspend fun listSdnZones(): Result<List<SdnZoneInfo>> = apiCall { api ->
        runCatching {
            api.sdnZones().data.orEmpty().map { SdnZoneInfo.fromMap(it) }
        }.getOrDefault(emptyList())
    }

    /** Pending apt updates + key package versions per node. */
    suspend fun listClusterUpdates(): Result<List<NodeUpdateSnapshot>> = apiCall { api ->
        val nodes = discoverNodeNames(api)
        nodes.map { node ->
            val updates = runCatching { api.aptUpdateList(node).data.orEmpty() }
                .getOrDefault(emptyList())
                .map { AptPackageUpdate.fromMap(it) }
                .sortedBy { it.packageName.orEmpty() }
            val versions = runCatching { api.aptVersions(node).data.orEmpty() }
                .getOrDefault(emptyList())
                .map { AptPackageVersion.fromMap(it) }
            NodeUpdateSnapshot(node = node, updates = updates, versions = versions)
        }
    }

    /** Refresh apt package list on a node (starts a task). */
    suspend fun refreshAptUpdates(node: String): Result<String> = apiCall { api ->
        api.aptUpdateRefresh(node).data ?: "OK"
    }

    /** Upgrade apt packages on a node (runs dist-upgrade, returns UPID task). */
    suspend fun aptUpgrade(node: String): Result<String> = apiCall { api ->
        api.aptUpgrade(node).data ?: "OK"
    }

    /** Upgrade a PVE 9 node via direct SSH execution (apt-get update && apt-get full-upgrade -y). */
    suspend fun sshUpgrade(
        node: String,
        onOutputLine: (String) -> Unit = {},
    ): Result<Int> {
        val s = sessionStore.session.value ?: return Result.failure(PveException("No active session"))
        val config = s.config

        if (config.host.equals("demo", ignoreCase = true)) {
            return simulateDemoSshUpgrade(node, onOutputLine)
        }

        val profile = sessionStore.listProfiles().firstOrNull { it.host == config.host }
        if (profile == null || profile.authMode != AuthMode.PASSWORD || !profile.hasSavedSecret || profile.password.isBlank()) {
            return Result.failure(PveException("SSH upgrade needs the saved password for this profile. Reconnect with Save credentials on, or use the node shell."))
        }

        val targetHost = config.host.trim().removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
        val sshUser = resolveSshUpgradeUser(config.username)

        val executor = SshUpgradeExecutor(
            getStoredFingerprint = { h -> sessionStore.getHostKey(h) },
            storeFingerprint = { h, k -> sessionStore.saveHostKey(h, k) },
        )

        return executor.executeUpgrade(
            host = targetHost,
            port = 22,
            username = sshUser,
            password = profile.password,
            onOutputLine = onOutputLine,
        )
    }

    private suspend fun simulateDemoSshUpgrade(
        node: String,
        onOutputLine: (String) -> Unit,
    ): Result<Int> {
        val fakeLines = listOf(
            "Connecting to $node:22...",
            "Authenticated as root using password.",
            "Running apt-get update && apt-get full-upgrade -y...",
            "Hit:1 http://deb.debian.org/debian bookworm InRelease",
            "Hit:2 http://deb.debian.org/debian bookworm-updates InRelease",
            "Hit:3 http://security.debian.org/debian-security bookworm-security InRelease",
            "Hit:4 http://download.proxmox.com/debian/pve bookworm InRelease",
            "Reading package lists...",
            "Building dependency tree...",
            "Reading state information...",
            "Calculating upgrade...",
            "The following packages will be upgraded: pve-manager proxmox-kernel-6.8 qemu-server openssl",
            "4 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.",
            "Need to get 0 B/84.2 MB of archives.",
            "Unpacking pve-manager (9.2.11) over (9.2.0)...",
            "Setting up pve-manager (9.2.11)...",
            "Setting up proxmox-kernel-6.8 (6.8.12-1)...",
            "Setting up qemu-server (9.0.2)...",
            "Setting up openssl (3.0.15-1~deb12u1)...",
            "Processing triggers for systemd (252.33-1~deb12u1)...",
            "Upgrade completed successfully (exit code 0).",
        )
        for (line in fakeLines) {
            onOutputLine(line)
            kotlinx.coroutines.delay(350L)
        }
        return Result.success(0)
    }

    suspend fun taskLog(
        node: String,
        upid: String,
        start: Int? = null,
        limit: Int? = 20,
    ): Result<List<String>> = apiCall { api ->
        val lines = api.taskLog(node, upid, start, limit).data.orEmpty()
        lines.mapNotNull { it["t"]?.toString() }
    }

    /** Full node ops bundle: status, services, recent tasks. */
    suspend fun loadNodeBundle(node: String): Result<NodeBundle> = apiCall { api ->
        val status = runCatching { api.nodeStatus(node).data }.getOrNull()
        val services = runCatching { api.nodeServices(node).data.orEmpty() }
            .getOrDefault(emptyList())
            .map { NodeServiceInfo.fromMap(it) }
            .sortedBy { it.name.orEmpty() }
        val tasks = runCatching { api.nodeTasks(node, start = 0, limit = 25).data.orEmpty() }
            .getOrDefault(emptyList())
            .map { NodeTaskInfo.fromMap(it) }
        NodeBundle(node = node, status = status, services = services, tasks = tasks)
    }

    suspend fun listSdnVnets(): Result<List<SdnVnetInfo>> = apiCall { api ->
        runCatching {
            api.sdnVnets().data.orEmpty().map { SdnVnetInfo.fromMap(it) }
        }.getOrDefault(emptyList())
    }

    suspend fun listSdnStatus(): Result<List<SdnStatusInfo>> = apiCall { api ->
        runCatching {
            api.sdnStatus().data.orEmpty().map { SdnStatusInfo.fromMap(it) }
        }.getOrDefault(emptyList())
    }

    /** Datacenter firewall options + rules (read-only). */
    suspend fun loadClusterFirewall(): Result<FirewallSnapshot> = apiCall { api ->
        val options = runCatching { api.clusterFirewallOptions().data.orEmpty() }
            .getOrDefault(emptyMap())
        val rules = runCatching { api.clusterFirewallRules().data.orEmpty() }
            .getOrDefault(emptyList())
            .map { FirewallRule.fromMap(it) }
        val aliases = runCatching { api.clusterFirewallAliases().data.orEmpty() }
            .getOrDefault(emptyList())
            .map { FirewallAlias.fromMap(it) }
        FirewallSnapshot(scope = "cluster", options = options, rules = rules, aliases = aliases)
    }

    suspend fun loadNodeFirewall(node: String): Result<FirewallSnapshot> = apiCall { api ->
        val options = runCatching { api.nodeFirewallOptions(node).data.orEmpty() }
            .getOrDefault(emptyMap())
        val rules = runCatching { api.nodeFirewallRules(node).data.orEmpty() }
            .getOrDefault(emptyList())
            .map { FirewallRule.fromMap(it) }
        FirewallSnapshot(scope = "node/$node", options = options, rules = rules, aliases = emptyList())
    }

    private suspend fun <T> apiCall(block: suspend (com.pxmx.app.data.api.ProxmoxApi) -> T): Result<T> {
        val session = sessionStore.session.value
            ?: return Result.failure(PveException("Not connected"))
        
        try {
            val api = clientFactory.apiFor(session.config)
            return Result.success(block(api))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            
            val is401 = e is HttpException && e.code() == 401
            val isPassword = session.config.authMode == AuthMode.PASSWORD
            
            if (is401 && isPassword) {
                val profileId = sessionStore.lastProfileId()
                val profile = profileId?.let { sessionStore.getProfile(it) }

                val (newSession, reAuthMethod) = authMutex.withLock {
                    val currentSession = sessionStore.session.value
                    if (currentSession?.ticket != session.ticket && currentSession != null) {
                        // Already re-logged in by another concurrent call
                        currentSession to null
                    } else {
                        // 1. Attempt PVE ticket renewal with OLD ticket as password
                        var renewed: SessionState? = null
                        if (!session.ticket.isNullOrBlank()) {
                            try {
                                val api = clientFactory.apiFor(session.config)
                                val user = normalizeUsername(session.config.username, session.config.realm)
                                val resp = api.createTicket(user, session.ticket)
                                val newTicket = resp.data?.ticket
                                val newCsrf = resp.data?.csrfPreventionToken
                                if (!newTicket.isNullOrBlank()) {
                                    val updated = session.copy(ticket = newTicket, csrf = newCsrf)
                                    sessionStore.setSession(updated)
                                    renewed = updated
                                }
                            } catch (renewE: Exception) {
                                if (renewE is CancellationException) throw renewE
                                // Ticket renewal failed; fall through to loginWithProfile
                            }
                        }

                        if (renewed != null) {
                            renewed to "renewal"
                        } else if (profile?.hasSavedSecret == true) {
                            when (val outcome = loginWithProfile(profile.id, silent = true)) {
                                is LoginOutcome.Success -> outcome.session to "profile"
                                else -> null to null
                            }
                        } else {
                            null to null
                        }
                    }
                }

                if (newSession != null) {
                    if (reAuthMethod != null) {
                        withContext(Dispatchers.Main) {
                            Toasts.show(context, "Session refreshed")
                        }
                    }
                    return try {
                        val newApi = clientFactory.apiFor(newSession.config)
                        // Invoke the SAME block lambda directly for the retry
                        Result.success(block(newApi))
                    } catch (retryE: Exception) {
                        if (retryE is CancellationException) throw retryE
                        Result.failure(mapError(retryE))
                    }
                }
            }
            return Result.failure(mapError(e))
        }
    }

    private fun normalizeUsername(username: String, realm: String): String {
        val u = username.trim()
        return if (u.contains('@')) u else "$u@$realm"
    }

    private fun mapError(e: Exception): Exception = when (e) {
        is PveHttpException -> PveException(formatHttpError(e.code, e.errorBody, e.httpMessage), e)
        is PveException -> PveException(redactSecrets(e.message) ?: e.message ?: "Unknown error", e.cause)
        is HttpException -> {
            val body = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            PveException(formatHttpError(e.code(), body, e.message()), e)
        }
        is IOException -> {
            val rootCertEx = generateSequence<Throwable>(e) { it.cause }
                .firstOrNull { it is java.security.cert.CertificateException }
            if (rootCertEx != null) {
                PveException(redactSecrets(rootCertEx.message) ?: "TLS certificate validation failed", e)
            } else {
                PveException("Network error: ${redactSecrets(e.message)}", e)
            }
        }
        else -> PveException(redactSecrets(e.message) ?: e::class.java.simpleName, e)
    }

    companion object {
        fun formatHttpError(code: Int, rawBody: String?, httpMessage: String?): String {
            val cleanBody = redactSecrets(rawBody)?.trim()
            var detail: String? = null

            if (!cleanBody.isNullOrBlank()) {
                val isNullDataOnly = cleanBody == "{\"data\":null}" ||
                    cleanBody == "{\"data\": null}" ||
                    cleanBody == "{\"data\":null}\n" ||
                    cleanBody == "data:null"

                if (cleanBody.startsWith("{") && cleanBody.endsWith("}")) {
                    try {
                        val elem = AppJson.parseToJsonElement(cleanBody)
                        if (elem is JsonObject) {
                            val msg = elem["message"]?.jsonPrimitive?.contentOrNull
                            val errors = elem["errors"]
                            val data = elem["data"]

                            if (!msg.isNullOrBlank()) {
                                detail = msg
                            } else if (errors is JsonObject && errors.isNotEmpty()) {
                                detail = errors.values.mapNotNull {
                                    if (it is JsonPrimitive) it.contentOrNull else it.toString()
                                }.firstOrNull { !it.isNullOrBlank() }
                            } else if (elem.keys == setOf("data") && (data is JsonNull || data == null)) {
                                detail = when (code) {
                                    401 -> "authentication failure"
                                    403 -> "forbidden"
                                    else -> null
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Keep fallback
                    }
                }

                if (detail.isNullOrBlank()) {
                    if (isNullDataOnly) {
                        detail = when (code) {
                            401 -> "authentication failure"
                            403 -> "forbidden"
                            else -> null
                        }
                    } else {
                        detail = cleanBody
                    }
                }
            }

            if (detail.isNullOrBlank()) {
                val msg = httpMessage?.trim()
                if (!msg.isNullOrBlank() && !msg.startsWith("HTTP $code") && !msg.equals("Response.error()", ignoreCase = true)) {
                    detail = msg.lowercase(java.util.Locale.US)
                }
            }

            if (detail.isNullOrBlank()) {
                detail = when (code) {
                    401 -> "authentication failure"
                    403 -> "forbidden"
                    404 -> "not found"
                    500 -> "internal server error"
                    501 -> "not implemented"
                    else -> "request failed"
                }
            }

            return "HTTP $code: $detail"
        }

        fun sanitizeBackupFilename(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) {
                throw PveException("Invalid backup filename '$name': contains path separators or traversal sequences")
            }
            if (!trimmed.matches(Regex("^[A-Za-z0-9._-]+$"))) {
                throw PveException("Invalid backup filename '$name': contains disallowed characters (allowed: [A-Za-z0-9._-])")
            }
            return trimmed
        }

        fun redactSecrets(raw: String?): String? {
            if (raw.isNullOrBlank()) return raw
            var sanitized = raw
            sanitized = sanitized.replace(
                Regex(""""(?:ticket|CSRFPreventionToken|password|secret|apiToken|vncticket)"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE)
            ) {
                val key = it.value.substringBefore(':')
                "$key:\"[REDACTED]\""
            }
            sanitized = sanitized.replace(Regex("""PVE:[A-Za-z0-9@_.:+/%=-]+"""), "[REDACTED_TICKET]")
            sanitized = sanitized.replace(Regex("""PVEAuthCookie=[^;\s]+"""), "PVEAuthCookie=[REDACTED]")
            sanitized = sanitized.replace(Regex("""PVEAPIToken=[^\s]+"""), "PVEAPIToken=[REDACTED]")
            return sanitized
        }

        const val SSH_UPGRADE_USER = "root"

        /**
         * PVE API users and Linux SSH users are separate identity systems.
         * apt-get full-upgrade requires root privileges; therefore SSH upgrade always connects as root.
         */
        fun resolveSshUpgradeUser(sessionUsername: String? = null): String = SSH_UPGRADE_USER
    }
}

class PveHttpException(
    val code: Int,
    val errorBody: String?,
    val httpMessage: String?,
    cause: Throwable? = null,
) : Exception("HTTP $code: ${errorBody ?: httpMessage}", cause)

class PveException(message: String, cause: Throwable? = null) : Exception(message, cause)
