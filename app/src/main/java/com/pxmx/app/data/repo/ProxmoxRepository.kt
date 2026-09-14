package com.pxmx.app.data.repo

import android.content.Context
import com.pxmx.app.data.api.ProxmoxApiProvider
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ConsoleSession
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestBundle
import com.pxmx.app.data.model.GuestStatus
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.LoginOutcome
import com.pxmx.app.data.model.NodeBundle
import com.pxmx.app.data.model.NodeNetworkSnapshot
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeUpdateSnapshot
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo
import com.pxmx.app.data.model.ServerConfig
import com.pxmx.app.data.model.ServerProbe
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.SiteInfo
import com.pxmx.app.data.model.StorageDetail
import com.pxmx.app.data.model.TaskStatus
import com.pxmx.app.data.model.VersionInfo
import com.pxmx.app.data.net.ConnectionTestResult
import com.pxmx.app.data.net.LocalNet
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActiveAptTask(
    val node: String,
    val upid: String,
    val type: String,
    val startTimeMs: Long = System.currentTimeMillis(),
)

/**
 * Top-level facade and coordinator for Proxmox VE operations.
 * Delegates specialized domains to cohesive domain repositories:
 * - [PveClient]: Network execution, auth retry mutex, error mapping, credential redaction
 * - [AuthRepository]: Authentication, TFA, profiles, auto-connect, session lifecycle
 * - [NodeRepository]: Cluster topology, resource aggregation, node status, syslog, tasks
 * - [GuestRepository]: VM (QEMU) & LXC containers, power actions, snapshots, USB passthrough
 * - [StorageRepository]: Storage metrics, content items, backups, MediaStore downloads
 * - [UpdateRepository]: Apt update checks, dist-upgrade tasks, SSH upgrade execution
 * - [ConsoleRepository]: noVNC and xterm.js proxy session URL generation
 * - [NetworkRepository]: SDN zones/vnets and datacenter/node firewall rules
 */
class ProxmoxRepository(
    private val context: Context,
    internal val sessionStore: SessionStore,
    private val clientFactory: ProxmoxApiProvider,
    val localNet: LocalNet = LocalNet(context, sessionStore),
) {
    internal val appContext: Context get() = context

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

    // -------------------------------------------------------------------------
    // Domain Repositories Wiring
    // -------------------------------------------------------------------------

    val pveClient: PveClient = PveClient(
        context = context,
        sessionStore = sessionStore,
        clientFactory = clientFactory,
        reAuthHandler = { profileId -> authRepo.loginWithProfile(profileId, silent = true) },
    )

    val authRepo: AuthRepository = AuthRepository(
        context = context,
        sessionStore = sessionStore,
        clientFactory = clientFactory,
        pveClient = pveClient,
        localNet = localNet,
        onLogout = {
            guestRepo.clearCache()
            nodeRepo.clearCache()
        },
    )

    val storageRepo: StorageRepository = StorageRepository(
        context = context,
        sessionStore = sessionStore,
        pveClient = pveClient,
        taskStatusProvider = { node, upid -> nodeRepo.taskStatus(node, upid) },
    )

    val guestRepo: GuestRepository = GuestRepository(
        pveClient = pveClient,
        recentActionRegistry = recentActionRegistry,
        loadBackupsForVmid = { api, node, vmid, storages ->
            storageRepo.loadBackupsForVmid(api, node, vmid, storages)
        },
    )

    val nodeRepo: NodeRepository = NodeRepository(
        pveClient = pveClient,
        sessionStore = sessionStore,
        clientFactory = clientFactory,
        activeTaskProvider = { _activeAptTask.value },
        updateTaskLogLine = { node, upid, type, line -> updateTaskLogLine(node, upid, type, line) },
        onClusterLogUpdated = { entries -> _clusterLogCache.value = entries },
        loadGuests = { api, node, guestType -> guestRepo.loadGuests(api, node, guestType) },
        loadStorage = { api, node -> storageRepo.loadStorage(api, node) },
    )

    val updateRepo: UpdateRepository = UpdateRepository(
        sessionStore = sessionStore,
        pveClient = pveClient,
        discoverNodeNames = { api -> nodeRepo.discoverNodeNames(api) },
    )

    val consoleRepo: ConsoleRepository = ConsoleRepository(
        sessionStore = sessionStore,
        pveClient = pveClient,
    )

    val networkRepo: NetworkRepository = NetworkRepository(
        pveClient = pveClient,
        discoverNodeNames = { api -> nodeRepo.discoverNodeNames(api) },
    )

    // -------------------------------------------------------------------------
    // Auth & Session Delegation
    // -------------------------------------------------------------------------

    suspend fun login(
        config: ServerConfig,
        saveCredentials: Boolean = true,
        profileId: String? = null,
        enableAutoConnect: Boolean? = null,
        label: String = "",
        forceNewProfile: Boolean = false,
        silent: Boolean = false,
    ): LoginOutcome = authRepo.login(
        config = config,
        saveCredentials = saveCredentials,
        profileId = profileId,
        enableAutoConnect = enableAutoConnect,
        label = label,
        forceNewProfile = forceNewProfile,
        silent = silent,
    )

    suspend fun completeTfa(
        config: ServerConfig,
        partialTicket: String,
        otp: String,
        saveCredentials: Boolean = true,
        profileId: String? = null,
        enableAutoConnect: Boolean? = null,
        label: String = "",
        forceNewProfile: Boolean = false,
    ): Result<SessionState> = authRepo.completeTfa(
        config = config,
        partialTicket = partialTicket,
        otp = otp,
        saveCredentials = saveCredentials,
        profileId = profileId,
        enableAutoConnect = enableAutoConnect,
        label = label,
        forceNewProfile = forceNewProfile,
    )

    suspend fun loginWithProfile(profileId: String, silent: Boolean = false): LoginOutcome =
        authRepo.loginWithProfile(profileId, silent)

    suspend fun probeProfile(profile: SavedProfile): Result<ServerProbe> =
        authRepo.probeProfile(profile)

    suspend fun testConnection(host: String, port: Int = 8006, timeoutMs: Long = 8000L): ConnectionTestResult =
        authRepo.testConnection(host, port, timeoutMs)

    suspend fun testProfileConnection(profile: SavedProfile): ConnectionTestResult =
        authRepo.testProfileConnection(profile)

    suspend fun tryAutoConnect(): Result<SessionState> =
        authRepo.tryAutoConnect()

    fun logout(rememberAsPrevious: Boolean = true) =
        authRepo.logout(rememberAsPrevious)

    // -------------------------------------------------------------------------
    // Node & Cluster Delegation
    // -------------------------------------------------------------------------

    suspend fun refreshVersion(): Result<VersionInfo> =
        nodeRepo.refreshVersion()

    suspend fun siteInfo(): Result<SiteInfo> =
        nodeRepo.siteInfo()

    suspend fun listResources(type: String? = null): Result<List<ClusterResource>> =
        nodeRepo.listResources(type)

    suspend fun nodeStatus(node: String): Result<NodeStatus> =
        nodeRepo.nodeStatus(node)

    suspend fun loadNodeBundle(node: String): Result<NodeBundle> =
        nodeRepo.loadNodeBundle(node)

    suspend fun listNodeNames(): Result<List<String>> =
        nodeRepo.listNodeNames()

    suspend fun listNodeStorageNames(node: String): Result<List<String>> =
        nodeRepo.listNodeStorageNames(node)

    suspend fun listClusterNetwork(): Result<List<NodeNetworkSnapshot>> =
        nodeRepo.listClusterNetwork()

    suspend fun clusterTasks(): Result<List<Map<String, Any>>> =
        nodeRepo.clusterTasks()

    suspend fun taskStatus(node: String, upid: String): Result<TaskStatus> =
        nodeRepo.taskStatus(node, upid)

    suspend fun awaitTask(
        node: String,
        upid: String,
        timeoutMs: Long = 60_000,
        intervalMs: Long = 1_000,
    ): Result<TaskStatus> = nodeRepo.awaitTask(node, upid, timeoutMs, intervalMs)

    suspend fun taskLog(
        node: String,
        upid: String,
        start: Int? = null,
        limit: Int? = 20,
    ): Result<List<String>> = nodeRepo.taskLog(node, upid, start, limit)

    suspend fun logPoll(max: Int = 10): Result<List<ClusterLogEntry>> =
        nodeRepo.logPoll(max)

    suspend fun logHistory(max: Int = 200): Result<List<ClusterLogEntry>> =
        nodeRepo.logHistory(max)

    suspend fun nodeSyslog(
        node: String,
        start: Int? = null,
        limit: Int? = 50,
    ): Result<List<ClusterLogEntry>> = nodeRepo.nodeSyslog(node, start, limit)

    // -------------------------------------------------------------------------
    // Guest (VM & Container) Delegation
    // -------------------------------------------------------------------------

    suspend fun guestStatus(node: String, guestType: GuestType, vmid: Long): Result<GuestStatus> =
        guestRepo.guestStatus(node, guestType, vmid)

    suspend fun guestAction(
        node: String,
        guestType: GuestType,
        vmid: Long,
        action: GuestAction,
    ): Result<String> = guestRepo.guestAction(node, guestType, vmid, action)

    suspend fun deployFromTemplate(
        source: ClusterResource,
        newId: Long,
        name: String,
    ): Result<String> = guestRepo.deployFromTemplate(source, newId, name)

    suspend fun loadGuestBundle(
        node: String,
        guestType: GuestType,
        vmid: Long,
    ): Result<GuestBundle> = guestRepo.loadGuestBundle(node, guestType, vmid)

    suspend fun attachUsb(
        node: String,
        guestType: GuestType,
        vmid: Long,
        hostId: String,
        usb3: Boolean = true,
    ): Result<String> = guestRepo.attachUsb(node, guestType, vmid, hostId, usb3)

    suspend fun detachUsb(
        node: String,
        guestType: GuestType,
        vmid: Long,
        usbKey: String,
    ): Result<String> = guestRepo.detachUsb(node, guestType, vmid, usbKey)

    suspend fun listHostUsb(node: String): Result<List<HostUsbDevice>> =
        guestRepo.listHostUsb(node)

    suspend fun setGuestOnboot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        enabled: Boolean,
    ): Result<String> = guestRepo.setGuestOnboot(node, guestType, vmid, enabled)

    suspend fun createSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
        description: String? = null,
        includeRam: Boolean = false,
    ): Result<String> = guestRepo.createSnapshot(node, guestType, vmid, name, description, includeRam)

    suspend fun deleteSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        snap: String,
    ): Result<String> = guestRepo.deleteSnapshot(node, guestType, vmid, snap)

    suspend fun rollbackSnapshot(
        node: String,
        guestType: GuestType,
        vmid: Long,
        snap: String,
    ): Result<String> = guestRepo.rollbackSnapshot(node, guestType, vmid, snap)

    // -------------------------------------------------------------------------
    // Storage & Backup Delegation
    // -------------------------------------------------------------------------

    suspend fun storageDetail(
        node: String,
        storage: String,
        contentFilter: String? = null,
    ): Result<StorageDetail> = storageRepo.storageDetail(node, storage, contentFilter)

    suspend fun deleteStorageVolume(node: String, volid: String): Result<String> =
        storageRepo.deleteStorageVolume(node, volid)

    suspend fun createBackup(
        node: String,
        vmid: Long,
        storage: String,
        mode: String = "snapshot",
        compress: String = "zstd",
    ): Result<String> = storageRepo.createBackup(node, vmid, storage, mode, compress)

    suspend fun deleteBackup(node: String, volid: String): Result<String> =
        storageRepo.deleteBackup(node, volid)

    suspend fun backupToDevice(
        node: String,
        type: String,
        vmid: Long,
        storage: String,
        onProgress: (String) -> Unit,
    ): Result<String> = storageRepo.backupToDevice(node, type, vmid, storage, onProgress)

    // -------------------------------------------------------------------------
    // Console Delegation
    // -------------------------------------------------------------------------

    suspend fun openConsole(
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
        cmd: String? = null,
    ): Result<ConsoleSession> = consoleRepo.openConsole(node, guestType, vmid, name, cmd)

    // -------------------------------------------------------------------------
    // SDN & Firewall Delegation
    // -------------------------------------------------------------------------

    suspend fun listSdnZones(): Result<List<SdnZoneInfo>> =
        networkRepo.listSdnZones()

    suspend fun createSdnZone(zone: String, type: String): Result<Unit> =
        networkRepo.createSdnZone(zone, type)

    suspend fun deleteSdnZone(zone: String): Result<Unit> =
        networkRepo.deleteSdnZone(zone)

    suspend fun listSdnVnets(): Result<List<SdnVnetInfo>> =
        networkRepo.listSdnVnets()

    suspend fun createSdnVnet(vnet: String, zone: String, alias: String? = null): Result<Unit> =
        networkRepo.createSdnVnet(vnet, zone, alias)

    suspend fun deleteSdnVnet(vnet: String): Result<Unit> =
        networkRepo.deleteSdnVnet(vnet)

    suspend fun listSdnStatus(): Result<List<SdnStatusInfo>> =
        networkRepo.listSdnStatus()

    suspend fun applySdn(): Result<String> =
        networkRepo.applySdn()

    suspend fun loadClusterFirewall(): Result<FirewallSnapshot> =
        networkRepo.loadClusterFirewall()

    suspend fun setClusterFirewallEnable(
        enable: Boolean,
        digest: String? = null,
    ): Result<Unit> = networkRepo.setClusterFirewallEnable(enable, digest)

    suspend fun loadNodeFirewall(node: String): Result<FirewallSnapshot> =
        networkRepo.loadNodeFirewall(node)

    suspend fun setNodeFirewallEnable(
        node: String,
        enable: Boolean,
        digest: String? = null,
    ): Result<Unit> = networkRepo.setNodeFirewallEnable(node, enable, digest)

    // -------------------------------------------------------------------------
    // Updates Delegation
    // -------------------------------------------------------------------------

    suspend fun listClusterUpdates(): Result<List<NodeUpdateSnapshot>> =
        updateRepo.listClusterUpdates()

    suspend fun refreshAptUpdates(node: String): Result<String> =
        updateRepo.refreshAptUpdates(node)

    suspend fun aptUpgrade(node: String): Result<String> =
        updateRepo.aptUpgrade(node)

    suspend fun sshUpgrade(
        node: String,
        onOutputLine: (String) -> Unit = {},
    ): Result<Int> = updateRepo.sshUpgrade(node, onOutputLine)

    // -------------------------------------------------------------------------
    // Companion Helpers (Backwards Compatibility)
    // -------------------------------------------------------------------------

    companion object {
        fun formatHttpError(code: Int, rawBody: String?, httpMessage: String?): String =
            PveClient.formatHttpError(code, rawBody, httpMessage)

        fun sanitizeBackupFilename(name: String): String =
            StorageRepository.sanitizeBackupFilename(name)

        fun redactSecrets(raw: String?): String? =
            PveClient.redactSecrets(raw)

        const val SSH_UPGRADE_USER = UpdateRepository.SSH_UPGRADE_USER

        fun resolveSshUpgradeUser(sessionUsername: String? = null): String =
            UpdateRepository.resolveSshUpgradeUser(sessionUsername)
    }
}
