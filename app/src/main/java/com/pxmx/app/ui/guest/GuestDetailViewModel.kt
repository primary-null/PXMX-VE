package com.pxmx.app.ui.guest

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestStatus
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.ParsedGuestConfig
import com.pxmx.app.data.model.SnapshotInfo
import com.pxmx.app.data.LivePoll
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.repo.filterLatestLogForStrip
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
import kotlinx.coroutines.launch

/** Accordion sections on the guest detail screen. */
enum class GuestSection(val title: String) {
    HARDWARE("Hardware"),
    NETWORK("Network & USB"),
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
    /** Null = all collapsed; otherwise that section is open (single-expand). */
    val expanded: GuestSection? = GuestSection.HARDWARE,
    val status: GuestStatus? = null,
    val config: ParsedGuestConfig? = null,
    val snapshots: List<SnapshotInfo> = emptyList(),
    val backups: List<BackupVolume> = emptyList(),
    val hostUsbs: List<HostUsbDevice> = emptyList(),
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
)

class GuestDetailViewModel(
    private val repository: ProxmoxRepository,
    node: String,
    guestType: GuestType,
    vmid: Long,
    name: String,
    private val context: Context = repository.appContext,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        GuestDetailUiState(node = node, guestType = guestType, vmid = vmid, name = name),
    )

    private val guestPollingFlow = tickerFlow(LivePoll.GUEST_MS, emitImmediately = false)
        .onEach {
            if (!_ui.value.loading && _ui.value.actionInProgress == null) {
                pollLiveStatus()
            }
        }

    private val logPollingFlow = tickerFlow(8_000L, emitImmediately = true)
        .onEach {
            repository.logPoll(max = 5)
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
        repository.clusterLogCache,
        repository.activeAptTask,
        repository.activeAptLogLine,
        repository.isUpdatesScreenActive,
    ) { entries, aptTask, aptLine, updatesActive ->
        val sessionUser = repository.sessionStore.session.value?.username
            ?: repository.sessionStore.session.value?.config?.username
        if (!updatesActive && aptTask != null && aptLine != null) {
            aptLine
        } else {
            filterLatestLogForStrip(
                entries = entries,
                recentRegistry = repository.recentActionRegistry,
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
        refresh()
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
            _ui.update { it.copy(loading = true, error = null) }
            repository.loadGuestBundle(s.node, s.guestType, s.vmid).fold(
                onSuccess = { bundle ->
                    _ui.update {
                        it.copy(
                            status = bundle.status,
                            config = bundle.config,
                            snapshots = bundle.snapshots,
                            backups = bundle.backups,
                            hostUsbs = bundle.hostUsbs,
                            backupStorages = bundle.backupStorages,
                            name = bundle.status?.name?.takeIf { n -> n.isNotBlank() }
                                ?: bundle.config.name?.takeIf { n -> n.isNotBlank() }
                                ?: it.name,
                            loading = false,
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(loading = false, error = e.message ?: "Failed to load guest")
                    }
                },
            )
        }
    }

    private suspend fun pollLiveStatus() {
        val s = _ui.value
        repository.guestStatus(s.node, s.guestType, s.vmid).onSuccess { status ->
            _ui.update {
                it.copy(
                    status = status,
                    name = status.name?.takeIf { n -> n.isNotBlank() } ?: it.name,
                )
            }
        }
    }

    fun runPowerAction(action: GuestAction) {
        val guestName = _ui.value.name
        runTask("power:${action.apiName}", action.label) { s ->
            repository.guestAction(s.node, s.guestType, s.vmid, action).fold(
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

    fun createSnapshot(name: String, description: String, includeRam: Boolean) {
        if (name.isBlank() || name == "current") {
            _ui.update { it.copy(error = "Invalid snapshot name") }
            return
        }
        _ui.update { it.copy(showCreateSnapshot = false) }
        runTask("snapshot:create", "Create snapshot") { s ->
            repository.createSnapshot(
                s.node, s.guestType, s.vmid, name.trim(),
                description.ifBlank { null }, includeRam,
            )
        }
    }

    fun deleteSnapshot(name: String) {
        _ui.update { it.copy(confirmDeleteSnap = null) }
        runTask("snapshot:delete", "Delete snapshot") { s ->
            repository.deleteSnapshot(s.node, s.guestType, s.vmid, name)
        }
    }

    fun rollbackSnapshot(name: String) {
        _ui.update { it.copy(confirmRollbackSnap = null) }
        runTask("snapshot:rollback", "Rollback") { s ->
            repository.rollbackSnapshot(s.node, s.guestType, s.vmid, name)
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
            repository.createBackup(s.node, s.vmid, storage, mode = mode)
        }
    }

    fun deleteBackup(vol: BackupVolume) {
        val volid = vol.volid ?: return
        _ui.update { it.copy(confirmDeleteBackup = null) }
        runTask("backup:delete", "Delete backup") { s ->
            repository.deleteBackup(s.node, volid)
        }
    }

    fun attachUsb(hostId: String, usb3: Boolean = true) {
        runTask("usb:attach", "Attach USB") { s ->
            repository.attachUsb(s.node, s.guestType, s.vmid, hostId, usb3)
        }
    }

    fun detachUsb(usbKey: String) {
        runTask("usb:detach", "Detach USB") { s ->
            repository.detachUsb(s.node, s.guestType, s.vmid, usbKey)
        }
    }

    fun refreshUsbOnly() {
        val s = _ui.value
        viewModelScope.launch {
            repository.listHostUsb(s.node).fold(
                onSuccess = { list -> _ui.update { it.copy(hostUsbs = list) } },
                onFailure = { e -> _ui.update { it.copy(error = e.message) } },
            )
            // Also reload config so attached list is current
            refresh()
        }
    }

    private fun runTask(
        key: String,
        label: String,
        block: suspend (GuestDetailUiState) -> Result<String>,
    ) {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = key, message = null, error = null) }
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
                    repository.awaitTask(s.node, upid, timeoutMs = 120_000).fold(
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
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val node: String,
        private val guestType: GuestType,
        private val vmid: Long,
        private val name: String,
        private val context: Context = repository.appContext,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GuestDetailViewModel(repository, node, guestType, vmid, name, context) as T
        }
    }
}
