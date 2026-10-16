package com.pxmx.app.ui.guest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.ConfigNet
import com.pxmx.app.data.model.GuestLiveInterface
import com.pxmx.app.data.model.GuestStatus
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.HistoryTimeframe
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.ParsedGuestConfig
import com.pxmx.app.data.model.RrdPoint
import com.pxmx.app.data.model.SnapshotInfo
import com.pxmx.app.data.util.TagUtils
import com.pxmx.app.data.LivePoll
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.repo.ActiveAptTask
import com.pxmx.app.data.repo.GuestRepository
import com.pxmx.app.data.repo.NetworkRepository
import com.pxmx.app.data.repo.NodeRepository
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.repo.RecentActionRegistry
import com.pxmx.app.data.repo.StorageRepository
import com.pxmx.app.data.repo.filterLatestLogForStrip
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.util.AppToast
import com.pxmx.app.ui.util.Toasts
import com.pxmx.app.ui.util.tickerFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.pxmx.app.ProxmoxApp
import com.pxmx.app.data.ops.OpsBoard
import com.pxmx.app.data.ops.OpsBoardStore
import com.pxmx.app.data.ops.computeAppliedClips
import com.pxmx.app.data.ops.computeAppliedKeys

/** Fact plates on the guest detail screen. */
enum class GuestSection(val title: String) {
    HARDWARE("Hardware"),
    NETWORK("Network & USB"),
    FIREWALL("Firewall"),
    OPTIONS("Options"),
    SNAPSHOTS("Snapshots"),
    BACKUPS("Backups"),
    RAW("Raw config"),
}

data class GuestDetailUiState(
    val node: String,
    val guestType: GuestType,
    val vmid: Long,
    val name: String,
    /** Null = OPS scan (all fact plates collapsed); otherwise that fact plate is open. */
    val expanded: GuestSection? = null,
    val board: OpsBoard = OpsBoard.DEFAULT,
    val isEditingBoard: Boolean = false,
    val pinHintShown: Boolean = false,
    val appliedClips: List<String> = computeAppliedClips(board),
    val appliedKeys: List<String> = computeAppliedKeys(board),
    val status: GuestStatus? = null,
    val config: ParsedGuestConfig? = null,
    val firewall: FirewallSnapshot? = null,
    val firewallError: String? = null,
    val snapshots: List<SnapshotInfo> = emptyList(),
    val backups: List<BackupVolume> = emptyList(),
    val hostUsbs: List<HostUsbDevice> = emptyList(),
    val liveInterfaces: List<GuestLiveInterface> = emptyList(),
    val backupStorages: List<String> = emptyList(),
    val loading: Boolean = true,
    val actionInProgress: String? = null,
    val message: String? = null,
    val error: String? = null,
    val showCreateSnapshot: Boolean = false,
    val showCreateBackup: Boolean = false,
    val confirmDeleteSnap: String? = null,
    val confirmRollbackSnap: String? = null,
    val confirmDeleteBackup: BackupVolume? = null,
    val isTokenSession: Boolean = false,
    val rrdData: List<RrdPoint> = emptyList(),
    val rrdTimeframe: HistoryTimeframe = HistoryTimeframe.HOUR,
)

class GuestDetailViewModel(
    private val guestRepo: GuestRepository,
    private val storageRepo: StorageRepository,
    private val nodeRepo: NodeRepository,
    private val networkRepo: NetworkRepository,
    private val sessionStore: SessionStore,
    private val recentActionRegistry: RecentActionRegistry,
    private val context: Context,
    clusterLogCache: StateFlow<List<ClusterLogEntry>>,
    activeAptTask: StateFlow<ActiveAptTask?>,
    activeAptLogLine: StateFlow<ClusterLogEntry?>,
    isUpdatesScreenActive: StateFlow<Boolean>,
    val node: String,
    val guestType: GuestType,
    val vmid: Long,
    val name: String,
    private val opsBoardStore: OpsBoardStore = (context.applicationContext as? ProxmoxApp)?.opsBoardStore ?: OpsBoardStore.create(context),
) : ViewModel() {

    private val _ui = MutableStateFlow(
        GuestDetailUiState(
            node = node,
            guestType = guestType,
            vmid = vmid,
            name = name,
            isTokenSession = sessionStore.session.value?.config?.authMode == AuthMode.API_TOKEN,
        ),
    )

    private val guestPollingFlow = tickerFlow(LivePoll.GUEST_MS, emitImmediately = false)
        .onEach {
            if (!_ui.value.loading && _ui.value.actionInProgress == null) {
                pollLiveStatus()
            }
        }

    private val logPollingFlow = tickerFlow(8_000L, emitImmediately = true)
        .onEach {
            nodeRepo.logPoll(max = 5)
        }

    val ui: StateFlow<GuestDetailUiState> = merge(
        _ui,
        guestPollingFlow.map { _ui.value },
        logPollingFlow.map { _ui.value },
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _ui.value,
    )

    val latestLog: StateFlow<ClusterLogEntry?> = combine(
        clusterLogCache,
        activeAptTask,
        activeAptLogLine,
        isUpdatesScreenActive,
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
                targetVmid = _ui.value.vmid,
                isUpdatesScreenActive = updatesActive,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            opsBoardStore.data.collect { payload ->
                val profileId = currentProfileId()
                val board = payload.resolveBoard(profileId, guestType.name.lowercase(), vmid)
                _ui.update {
                    it.copy(
                        board = board,
                        pinHintShown = payload.pinHintShown,
                        appliedClips = computeAppliedClips(board),
                        appliedKeys = computeAppliedKeys(board),
                    )
                }
            }
        }
        refresh()
    }

    fun currentProfileId(): String {
        val host = sessionStore.session.value?.config?.host
        if (host != null && host.equals("demo", ignoreCase = true)) {
            return "demo"
        }
        return sessionStore.snapshot()?.profileId
            ?: sessionStore.lastProfileId()
            ?: "session"
    }

    fun toggleEditBoard() {
        _ui.update { it.copy(isEditingBoard = !it.isEditingBoard) }
    }

    fun consumeMessage(expected: String) {
        _ui.update { current ->
            if (current.message == expected) current.copy(message = null) else current
        }
    }

    fun consumeError(expected: String) {
        _ui.update { current ->
            if (current.error == expected) current.copy(error = null) else current
        }
    }

    fun togglePinSection(section: GuestSection) {
        if (section == GuestSection.RAW) return
        val currentBoard = _ui.value.board
        val wasPinned = currentBoard.isPinned(section)
        if (!wasPinned && currentBoard.pinnedSections.size >= 4) {
            _ui.update { it.copy(message = "PIN CAP 4") }
            return
        }
        val newBoard = currentBoard.togglePin(section)
        val profileId = currentProfileId()
        val showHint = !wasPinned && !_ui.value.pinHintShown
        _ui.update {
            it.copy(
                board = newBoard,
                pinHintShown = if (showHint) true else it.pinHintShown,
                appliedClips = computeAppliedClips(newBoard),
                appliedKeys = computeAppliedKeys(newBoard),
                message = if (showHint) "PINNED TO OPS · EDIT REORDERS THE DECK" else it.message,
            )
        }
        viewModelScope.launch {
            opsBoardStore.saveGuestBoard(profileId, guestType, vmid, newBoard)
            if (showHint) {
                opsBoardStore.setPinHintShown(true)
            }
        }
    }

    fun pinSection(section: GuestSection) {
        if (section == GuestSection.RAW) return
        val currentBoard = _ui.value.board
        if (currentBoard.isPinned(section)) return
        if (currentBoard.pinnedSections.size >= 4) {
            _ui.update { it.copy(message = "PIN CAP 4") }
            return
        }
        val newBoard = currentBoard.pinSection(section)
        val profileId = currentProfileId()
        val showHint = !_ui.value.pinHintShown
        _ui.update {
            it.copy(
                board = newBoard,
                pinHintShown = if (showHint) true else it.pinHintShown,
                appliedClips = computeAppliedClips(newBoard),
                appliedKeys = computeAppliedKeys(newBoard),
                message = if (showHint) "PINNED TO OPS · EDIT REORDERS THE DECK" else it.message,
            )
        }
        viewModelScope.launch {
            opsBoardStore.saveGuestBoard(profileId, guestType, vmid, newBoard)
            if (showHint) {
                opsBoardStore.setPinHintShown(true)
            }
        }
    }

    fun unpinSection(section: GuestSection) {
        if (section == GuestSection.RAW) return
        val currentBoard = _ui.value.board
        if (!currentBoard.isPinned(section)) return
        val newBoard = currentBoard.unpinSection(section)
        val profileId = currentProfileId()
        _ui.update {
            it.copy(
                board = newBoard,
                appliedClips = computeAppliedClips(newBoard),
                appliedKeys = computeAppliedKeys(newBoard),
            )
        }
        viewModelScope.launch {
            opsBoardStore.saveGuestBoard(profileId, guestType, vmid, newBoard)
        }
    }

    fun toggleConsole(include: Boolean) {
        val currentBoard = _ui.value.board
        val newBoard = currentBoard.toggleConsole(include)
        val profileId = currentProfileId()
        _ui.update {
            it.copy(
                board = newBoard,
                appliedClips = computeAppliedClips(newBoard),
                appliedKeys = computeAppliedKeys(newBoard),
            )
        }
        viewModelScope.launch {
            opsBoardStore.saveGuestBoard(profileId, guestType, vmid, newBoard)
        }
    }

    fun toggleHistory(include: Boolean) {
        val currentBoard = _ui.value.board
        val newBoard = currentBoard.toggleHistory(include)
        val profileId = currentProfileId()
        _ui.update {
            it.copy(
                board = newBoard,
                appliedClips = computeAppliedClips(newBoard),
                appliedKeys = computeAppliedKeys(newBoard),
            )
        }
        viewModelScope.launch {
            opsBoardStore.saveGuestBoard(profileId, guestType, vmid, newBoard)
        }
    }

    fun moveClipUp(clipId: String) {
        val currentBoard = _ui.value.board
        val newBoard = currentBoard.moveClipUp(clipId)
        val profileId = currentProfileId()
        _ui.update {
            it.copy(
                board = newBoard,
                appliedClips = computeAppliedClips(newBoard),
                appliedKeys = computeAppliedKeys(newBoard),
            )
        }
        viewModelScope.launch {
            opsBoardStore.saveGuestBoard(profileId, guestType, vmid, newBoard)
        }
    }

    fun moveClipDown(clipId: String) {
        val currentBoard = _ui.value.board
        val newBoard = currentBoard.moveClipDown(clipId)
        val profileId = currentProfileId()
        _ui.update {
            it.copy(
                board = newBoard,
                appliedClips = computeAppliedClips(newBoard),
                appliedKeys = computeAppliedKeys(newBoard),
            )
        }
        viewModelScope.launch {
            opsBoardStore.saveGuestBoard(profileId, guestType, vmid, newBoard)
        }
    }

    fun saveBoardAsProfileDefault() {
        val profileId = currentProfileId()
        val currentBoard = _ui.value.board
        viewModelScope.launch {
            opsBoardStore.setProfileDefault(profileId, currentBoard)
            opsBoardStore.clearGuestBoard(profileId, guestType, vmid)
            _ui.update { it.copy(message = "Board saved as default for all guests") }
        }
    }

    fun toggleSection(section: GuestSection) {
        _ui.update {
            it.copy(expanded = if (it.expanded == section) null else section)
        }
    }

    /** Jump to a section from the light tab strip (expand that one only). */
    fun selectSection(section: GuestSection?) {
        _ui.update { it.copy(expanded = section) }
    }

    fun refresh() {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, firewallError = null) }
            val tf = s.rrdTimeframe
            val rrdResult = guestRepo.guestRrdData(s.node, s.guestType, s.vmid, tf.apiName)
            val fwResult = networkRepo.loadGuestFirewall(s.node, s.guestType, s.vmid)
            val liveIfaces = guestRepo.fetchLiveInterfaces(s.node, s.guestType == GuestType.QEMU, s.vmid)
            guestRepo.loadGuestBundle(s.node, s.guestType, s.vmid).fold(
                onSuccess = { bundle ->
                    _ui.update {
                        it.copy(
                            status = bundle.status,
                            config = bundle.config,
                            rrdData = if (it.rrdTimeframe == tf) {
                                rrdResult.getOrDefault(it.rrdData)
                            } else {
                                it.rrdData
                            },
                            firewall = fwResult.getOrNull(),
                            firewallError = fwResult.exceptionOrNull()?.message,
                            liveInterfaces = liveIfaces,
                            snapshots = if ("snapshots" in bundle.sectionErrors) it.snapshots else bundle.snapshots,
                            backups = if ("backups" in bundle.sectionErrors) it.backups else
                                (bundle.backups + it.backups.filter { backup ->
                                    "backups/${backup.volid?.substringBefore(':')}" in bundle.sectionErrors
                                }).distinctBy { backup -> backup.volid }.sortedByDescending { backup -> backup.ctime ?: 0L },
                            hostUsbs = if ("USB" in bundle.sectionErrors) it.hostUsbs else bundle.hostUsbs,
                            backupStorages = if ("backup storages" in bundle.sectionErrors) it.backupStorages else bundle.backupStorages,
                            error = bundle.sectionErrors.takeIf { errors -> errors.isNotEmpty() }?.entries
                                ?.joinToString("\n") { (section, message) -> "$section unavailable (retained data may be stale): $message" },
                            name = bundle.status?.name?.takeIf { n -> n.isNotBlank() }
                                ?: bundle.config.name?.takeIf { n -> n.isNotBlank() }
                                ?: it.name,
                            loading = false,
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Failed to load guest",
                            firewall = fwResult.getOrNull(),
                            firewallError = fwResult.exceptionOrNull()?.message,
                        )
                    }
                },
            )
        }
    }

    private suspend fun pollLiveStatus() {
        val s = _ui.value
        guestRepo.guestStatus(s.node, s.guestType, s.vmid).onSuccess { status ->
            _ui.update {
                it.copy(
                    status = status,
                    name = status.name?.takeIf { n -> n.isNotBlank() } ?: it.name,
                )
            }
        }
    }

    fun runPowerAction(action: GuestAction) {
        if (_ui.value.actionInProgress != null) return
        val guestName = _ui.value.name
        runTask("power:${action.apiName}", action.label) { s ->
            guestRepo.guestAction(s.node, s.guestType, s.vmid, action).fold(
                onSuccess = { upid ->
                    showGuestActionToast(action, guestName)
                    Result.success(upid)
                },
                onFailure = { e ->
                    AppToast.ACTION_FAILED.show(context, action.label, e.message ?: "Failed")
                    Result.failure(e)
                }
            )
        }
    }

    private fun showGuestActionToast(action: GuestAction, guestName: String) {
        when (action) {
            GuestAction.START -> AppToast.GUEST_STARTED.show(context, guestName)
            GuestAction.STOP -> AppToast.GUEST_STOPPED.show(context, guestName)
            GuestAction.REBOOT -> AppToast.GUEST_REBOOTED.show(context, guestName)
            GuestAction.SHUTDOWN -> AppToast.GUEST_SHUTDOWN.show(context, guestName)
            GuestAction.SUSPEND -> AppToast.GUEST_SUSPENDED.show(context, guestName)
            GuestAction.RESUME -> AppToast.GUEST_RESUMED.show(context, guestName)
            else -> Toasts.show(context, "$guestName: ${action.label} sent")
        }
    }

    fun setRrdTimeframe(timeframe: HistoryTimeframe) {
        if (_ui.value.rrdTimeframe == timeframe) return
        val previous = _ui.value.rrdTimeframe
        _ui.update { it.copy(rrdTimeframe = timeframe) }
        viewModelScope.launch {
            val s = _ui.value
            guestRepo.guestRrdData(s.node, s.guestType, s.vmid, timeframe.apiName).fold(
                onSuccess = { points ->
                    _ui.update {
                        if (it.rrdTimeframe == timeframe) {
                            it.copy(rrdData = points, error = null)
                        } else {
                            it
                        }
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _ui.update {
                        if (it.rrdTimeframe == timeframe) {
                            it.copy(
                                rrdTimeframe = previous,
                                error = e.message ?: "Failed to load history",
                            )
                        } else {
                            it
                        }
                    }
                },
            )
        }
    }

    fun openCreateSnapshot(open: Boolean) {
        _ui.update { it.copy(showCreateSnapshot = open) }
    }

    fun openCreateBackup(open: Boolean) {
        _ui.update { it.copy(showCreateBackup = open) }
    }

    fun confirmDeleteSnapshot(name: String?) {
        _ui.update { it.copy(confirmDeleteSnap = name) }
    }

    fun confirmRollback(name: String?) {
        _ui.update { it.copy(confirmRollbackSnap = name) }
    }

    fun confirmDeleteBackup(vol: BackupVolume?) {
        _ui.update { it.copy(confirmDeleteBackup = vol) }
    }

    fun addTag(newTag: String) {
        val trimmed = newTag.trim()
        if (!TagUtils.isValidTag(trimmed)) return
        val currentTags = _ui.value.config?.tags ?: _ui.value.status?.tags
        val updated = TagUtils.addTag(currentTags, trimmed)
        val s = _ui.value
        viewModelScope.launch {
            guestRepo.updateGuestTags(s.node, s.guestType, s.vmid, updated).fold(
                onSuccess = {
                    refresh()
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(error = e.message ?: "Failed to add tag") }
                }
            )
        }
    }

    fun removeTag(tagToRemove: String) {
        val currentTags = _ui.value.config?.tags ?: _ui.value.status?.tags
        val updated = TagUtils.removeTag(currentTags, tagToRemove)
        val s = _ui.value
        viewModelScope.launch {
            guestRepo.updateGuestTags(s.node, s.guestType, s.vmid, updated).fold(
                onSuccess = {
                    refresh()
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(error = e.message ?: "Failed to remove tag") }
                }
            )
        }
    }

    fun createSnapshot(name: String, description: String, includeRam: Boolean) {
        if (name.isBlank() || name == "current") {
            _ui.update { it.copy(error = "Invalid snapshot name") }
            return
        }
        _ui.update { it.copy(showCreateSnapshot = false) }
        runTask("snapshot:create", "Create snapshot") { s ->
            guestRepo.createSnapshot(
                s.node, s.guestType, s.vmid, name.trim(),
                description.ifBlank { null }, includeRam,
            )
        }
    }

    fun deleteSnapshot(name: String) {
        _ui.update { it.copy(confirmDeleteSnap = null) }
        runTask("snapshot:delete", "Delete snapshot") { s ->
            guestRepo.deleteSnapshot(s.node, s.guestType, s.vmid, name)
        }
    }

    fun rollbackSnapshot(name: String) {
        _ui.update { it.copy(confirmRollbackSnap = null) }
        runTask("snapshot:rollback", "Rollback") { s ->
            guestRepo.rollbackSnapshot(s.node, s.guestType, s.vmid, name)
        }
    }

    fun createBackup(storage: String, mode: String) {
        if (storage.isBlank()) {
            _ui.update { it.copy(error = "Select a backup storage") }
            return
        }
        _ui.update { it.copy(showCreateBackup = false) }
        AppToast.BACKUP_SERVER_STARTED.show(context)
        runTask("backup:create", "Backup") { s ->
            storageRepo.createBackup(s.node, s.vmid, storage, mode = mode)
        }
    }

    fun deleteBackup(vol: BackupVolume) {
        val volid = vol.volid ?: return
        _ui.update { it.copy(confirmDeleteBackup = null) }
        runTask("backup:delete", "Delete backup") { s ->
            storageRepo.deleteBackup(s.node, volid)
        }
    }

    fun attachUsb(hostId: String, usb3: Boolean = true) {
        runTask("usb:attach", "Attach USB") { s ->
            guestRepo.attachUsb(s.node, s.guestType, s.vmid, hostId, usb3)
        }
    }

    fun detachUsb(usbKey: String) {
        runTask("usb:detach", "Detach USB") { s ->
            guestRepo.detachUsb(s.node, s.guestType, s.vmid, usbKey)
        }
    }

    fun refreshUsbOnly() {
        val s = _ui.value
        viewModelScope.launch {
            guestRepo.listHostUsb(s.node).fold(
                onSuccess = { list -> _ui.update { it.copy(hostUsbs = list) } },
                onFailure = { e -> _ui.update { it.copy(error = e.message) } },
            )
            // Also reload config so attached list is current
            refresh()
        }
    }

    fun toggleNicFirewall(net: ConfigNet) {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = "net:firewall", message = null, error = null) }
            try {
                guestRepo.updateNicOption(
                    node = s.node,
                    guestType = s.guestType,
                    vmid = s.vmid,
                    net = net,
                    toggleFirewall = true,
                ).fold(
                    onSuccess = {
                        _ui.update { it.copy(actionInProgress = null, message = "Updated ${net.key} firewall") }
                        refresh()
                    },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        _ui.update { it.copy(actionInProgress = null, error = e.message ?: "Failed to update firewall") }
                    }
                )
            } catch (e: CancellationException) {
                _ui.update { it.copy(actionInProgress = null) }
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(actionInProgress = null, error = e.message ?: "Failed to update firewall") }
            }
        }
    }

    fun toggleNicLinkDown(net: ConfigNet) {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = "net:linkdown", message = null, error = null) }
            try {
                guestRepo.updateNicOption(
                    node = s.node,
                    guestType = s.guestType,
                    vmid = s.vmid,
                    net = net,
                    toggleLinkDown = true,
                ).fold(
                    onSuccess = {
                        _ui.update { it.copy(actionInProgress = null, message = "Updated ${net.key} link") }
                        refresh()
                    },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        _ui.update { it.copy(actionInProgress = null, error = e.message ?: "Failed to update link") }
                    }
                )
            } catch (e: CancellationException) {
                _ui.update { it.copy(actionInProgress = null) }
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(actionInProgress = null, error = e.message ?: "Failed to update link") }
            }
        }
    }


    private fun runTask(
        key: String,
        label: String,
        block: suspend (GuestDetailUiState) -> Result<String>,
    ) {
        if (_ui.value.actionInProgress != null) return
        _ui.update { it.copy(actionInProgress = key, message = null, error = null) }
        val s = _ui.value
        viewModelScope.launch {
            try {
                block(s).fold(
                    onSuccess = { upid ->
                        if (upid.isBlank() || upid == "OK" || !upid.startsWith("UPID:")) {
                            _ui.update {
                                it.copy(message = "$label OK", actionInProgress = null)
                            }
                            refresh()
                            return@fold
                        }
                        _ui.update { it.copy(message = "$label started…") }
                        nodeRepo.awaitTask(s.node, upid, timeoutMs = 120_000).fold(
                            onSuccess = { task ->
                                val msg = if (task.isOk) {
                                    "$label completed"
                                } else {
                                    "$label: ${task.exitstatus ?: task.status}"
                                }
                                _ui.update { it.copy(message = msg, actionInProgress = null) }
                                refresh()
                            },
                            onFailure = {
                                _ui.update {
                                    it.copy(
                                        message = "$label submitted (still running). UPID: $upid",
                                        actionInProgress = null,
                                    )
                                }
                                refresh()
                            },
                        )
                    },
                    onFailure = { e ->
                        _ui.update {
                            it.copy(actionInProgress = null, error = e.message ?: "$label failed")
                        }
                    },
                )
            } catch (e: CancellationException) {
                _ui.update { it.copy(actionInProgress = null) }
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(actionInProgress = null, error = e.message ?: "$label failed")
                }
            }
        }
    }

    constructor(
        repository: ProxmoxRepository,
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
        context: Context = repository.appContext,
        opsBoardStore: OpsBoardStore = (context.applicationContext as? ProxmoxApp)?.opsBoardStore ?: OpsBoardStore.create(context),
    ) : this(
        guestRepo = repository.guestRepo,
        storageRepo = repository.storageRepo,
        nodeRepo = repository.nodeRepo,
        networkRepo = repository.networkRepo,
        sessionStore = repository.sessionStore,
        recentActionRegistry = repository.recentActionRegistry,
        context = context,
        opsBoardStore = opsBoardStore,
        clusterLogCache = repository.clusterLogCache,
        activeAptTask = repository.activeAptTask,
        activeAptLogLine = repository.activeAptLogLine,
        isUpdatesScreenActive = repository.isUpdatesScreenActive,
        node = node,
        guestType = guestType,
        vmid = vmid,
        name = name,
    )

    class Factory(
        private val repository: ProxmoxRepository,
        private val node: String,
        private val guestType: GuestType,
        private val vmid: Long,
        private val name: String,
        private val context: Context = repository.appContext,
        private val opsBoardStore: OpsBoardStore = (context.applicationContext as? ProxmoxApp)?.opsBoardStore ?: OpsBoardStore.create(context),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GuestDetailViewModel(
                guestRepo = repository.guestRepo,
                storageRepo = repository.storageRepo,
                nodeRepo = repository.nodeRepo,
                networkRepo = repository.networkRepo,
                sessionStore = repository.sessionStore,
                recentActionRegistry = repository.recentActionRegistry,
                context = context,
                opsBoardStore = opsBoardStore,
                clusterLogCache = repository.clusterLogCache,
                activeAptTask = repository.activeAptTask,
                activeAptLogLine = repository.activeAptLogLine,
                isUpdatesScreenActive = repository.isUpdatesScreenActive,
                node = node,
                guestType = guestType,
                vmid = vmid,
                name = name,
            ) as T
        }
    }
}
