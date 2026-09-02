package com.pxmx.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.SavedProfile
import com.pxmx.app.data.model.SessionState
import com.pxmx.app.data.model.SiteInfo
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.data.LivePoll
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.util.AppToast
import com.pxmx.app.ui.util.Toasts
import com.pxmx.app.ui.util.tickerFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ResourceFilter(val label: String, val type: String?) {
    ALL("All", null),
    GUESTS("Guests", null),
    NODES("Nodes", "node"),
    STORAGE("Storage", "storage"),
}

/**
 * Sort modes available in the overflow menu. Each tab keeps its own selection;
 * [optionsFor] returns only modes that make sense for that tab.
 */
enum class ResourceSort(val label: String, val menuHint: String) {
    /** Guests: status sections. Nodes/storage: name. */
    DEFAULT("Default", "Status groups / name"),
    USAGE("Most usage", "CPU + RAM load"),
    RAM("Most RAM", "Memory associated"),
    DISK("Most disk", "Disk associated"),
    NAME("Name A–Z", "Alphabetical"),
    CAPACITY("Largest capacity", "Total size"),
    FREE("Most free space", "Available space"),
    USED("Most used space", "Bytes consumed"),
    ;

    companion object {
        fun optionsFor(filter: ResourceFilter): List<ResourceSort> = when (filter) {
            ResourceFilter.GUESTS -> listOf(DEFAULT, USAGE, RAM, DISK, NAME)
            ResourceFilter.STORAGE -> listOf(NAME, USED, FREE, CAPACITY, USAGE)
            ResourceFilter.NODES -> listOf(DEFAULT, USAGE, RAM, NAME)
            ResourceFilter.ALL -> listOf(DEFAULT, USAGE, RAM, DISK, NAME)
        }
    }
}

/** Flat list rows for home: optional status section headers + resource cards. */
sealed class HomeListRow {
    data class Section(val title: String, val count: Int) : HomeListRow()
    data class Item(val resource: ClusterResource) : HomeListRow()
}

data class HomeUiState(
    val session: SessionState? = null,
    val site: SiteInfo? = null,
    val resources: List<ClusterResource> = emptyList(),
    val filter: ResourceFilter = ResourceFilter.GUESTS,
    val searchQuery: String = "",
    /** Independent sort preference per tab. */
    val sortGuests: ResourceSort = ResourceSort.DEFAULT,
    val sortStorage: ResourceSort = ResourceSort.NAME,
    val sortNodes: ResourceSort = ResourceSort.DEFAULT,
    val sortAll: ResourceSort = ResourceSort.DEFAULT,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val showAccounts: Boolean = false,
    val showThemePicker: Boolean = false,
    val showDeployDialog: Boolean = false,
    /** Guest id currently running a quick action (power/reboot/onboot). */
    val busyGuestIds: Set<String> = emptySet(),
    val deploying: Boolean = false,
) {
    val activeSort: ResourceSort
        get() = when (filter) {
            ResourceFilter.GUESTS -> sortGuests
            ResourceFilter.STORAGE -> sortStorage
            ResourceFilter.NODES -> sortNodes
            ResourceFilter.ALL -> sortAll
        }

    val filtered: List<ClusterResource>
        get() {
            val base = when (filter) {
                ResourceFilter.ALL -> resources
                ResourceFilter.GUESTS -> resources.filter { it.type == "qemu" || it.type == "lxc" }
                ResourceFilter.NODES -> resources.filter { it.type == "node" }
                ResourceFilter.STORAGE -> resources.filter { it.type == "storage" }
            }
            val q = searchQuery.trim().lowercase()
            val searched = if (q.isEmpty()) {
                base
            } else {
                base.filter { r ->
                    r.displayName.lowercase().contains(q) ||
                        r.node.orEmpty().lowercase().contains(q) ||
                        r.vmid?.toString()?.contains(q) == true ||
                        r.storage.orEmpty().lowercase().contains(q) ||
                        r.tags.orEmpty().lowercase().contains(q) ||
                        r.status.orEmpty().lowercase().contains(q)
                }
            }
            return searched.sortedWith(resourceComparator(filter, activeSort))
        }

    /**
     * Status section headers only for guests (or guest blocks in All) when using Default sort.
     * Metric sorts are a flat ranked list so “most usage” is obvious top-to-bottom.
     */
    val listRows: List<HomeListRow>
        get() {
            val list = filtered
            if (list.isEmpty()) return emptyList()
            val sort = activeSort
            return when (filter) {
                ResourceFilter.GUESTS -> {
                    if (sort == ResourceSort.DEFAULT) buildGuestSections(list)
                    else list.map { HomeListRow.Item(it) }
                }
                ResourceFilter.ALL -> {
                    if (sort == ResourceSort.DEFAULT) buildMixedRows(list)
                    else list.map { HomeListRow.Item(it) }
                }
                else -> list.map { HomeListRow.Item(it) }
            }
        }

    private fun buildGuestSections(list: List<ClusterResource>): List<HomeListRow> {
        val groups = linkedMapOf<String, MutableList<ClusterResource>>()
        for (r in list) {
            groups.getOrPut(r.guestStatusSection) { mutableListOf() }.add(r)
        }
        val rows = mutableListOf<HomeListRow>()
        for ((title, items) in groups) {
            if (items.isEmpty()) continue
            rows += HomeListRow.Section(title, items.size)
            items.forEach { rows += HomeListRow.Item(it) }
        }
        return rows
    }

    private fun buildMixedRows(list: List<ClusterResource>): List<HomeListRow> {
        val rows = mutableListOf<HomeListRow>()
        var lastGuestSection: String? = null
        for (r in list) {
            if (r.isGuest) {
                val section = r.guestStatusSection
                if (section != lastGuestSection) {
                    rows += HomeListRow.Section(
                        section,
                        list.count { it.isGuest && it.guestStatusSection == section },
                    )
                    lastGuestSection = section
                }
            } else {
                lastGuestSection = null
            }
            rows += HomeListRow.Item(r)
        }
        return rows
    }

    companion object {
        fun resourceComparator(
            filter: ResourceFilter,
            sort: ResourceSort,
        ): Comparator<ClusterResource> {
            val byNode = compareBy<ClusterResource> { it.node ?: "" }
            val byVmid = compareBy<ClusterResource> { it.vmid ?: 0L }
            val byName = compareBy<ClusterResource> { it.displayName.lowercase() }
            val byStatus = compareBy<ClusterResource> { it.guestStatusRank }
            val byType = compareBy<ClusterResource> { typeOrder(it.type) }
            val tieBreak = byNode.then(byVmid).then(byName)

            val byUsageDesc = compareByDescending<ClusterResource> { it.usageScore }
            val byRamDesc = compareByDescending<ClusterResource> {
                // “Associated” RAM: configured max, then live use.
                it.maxmem ?: it.mem ?: 0L
            }
            val byDiskAssocDesc = compareByDescending<ClusterResource> {
                it.maxdisk ?: it.disk ?: 0L
            }
            val byDiskUsedDesc = compareByDescending<ClusterResource> { it.disk ?: 0L }
            val byFreeDesc = compareByDescending<ClusterResource> { it.freeBytes ?: -1L }
            val byCapacityDesc = compareByDescending<ClusterResource> { it.maxdisk ?: 0L }

            return when (filter) {
                ResourceFilter.GUESTS -> when (sort) {
                    ResourceSort.DEFAULT -> byStatus.then(tieBreak)
                    ResourceSort.USAGE -> byUsageDesc.then(tieBreak)
                    ResourceSort.RAM -> byRamDesc.then(tieBreak)
                    ResourceSort.DISK -> byDiskAssocDesc.then(tieBreak)
                    ResourceSort.NAME -> byName.then(byNode).then(byVmid)
                    else -> byStatus.then(tieBreak)
                }

                ResourceFilter.STORAGE -> when (sort) {
                    ResourceSort.NAME -> byName.then(byNode)
                    ResourceSort.USED -> byDiskUsedDesc.then(byName)
                    ResourceSort.FREE -> byFreeDesc.then(byName)
                    ResourceSort.CAPACITY -> byCapacityDesc.then(byName)
                    ResourceSort.USAGE -> byUsageDesc.then(byDiskUsedDesc).then(byName)
                    else -> byName.then(byNode)
                }

                ResourceFilter.NODES -> when (sort) {
                    ResourceSort.DEFAULT -> byName
                    ResourceSort.USAGE -> byUsageDesc.then(byName)
                    ResourceSort.RAM -> byRamDesc.then(byName)
                    ResourceSort.NAME -> byName
                    else -> byName
                }

                ResourceFilter.ALL -> when (sort) {
                    ResourceSort.DEFAULT -> byType.then(byStatus).then(tieBreak)
                    ResourceSort.USAGE -> byUsageDesc.then(byType).then(tieBreak)
                    ResourceSort.RAM -> byRamDesc.then(byType).then(tieBreak)
                    ResourceSort.DISK -> byDiskAssocDesc.then(byType).then(tieBreak)
                    ResourceSort.NAME -> byName.then(byType).then(byNode)
                    else -> byType.then(byStatus).then(tieBreak)
                }
            }
        }

        private fun typeOrder(type: String?): Int = when (type) {
            "node" -> 0
            "qemu" -> 1
            "lxc" -> 2
            "storage" -> 3
            else -> 9
        }
    }
}

class HomeViewModel(
    private val repository: ProxmoxRepository,
    private val sessionStore: SessionStore,
    private val context: Context = repository.appContext,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        HomeUiState(
            session = sessionStore.session.value,
            sortGuests = sortFromStore("guests", ResourceSort.DEFAULT),
            sortStorage = sortFromStore("storage", ResourceSort.NAME),
            sortNodes = sortFromStore("nodes", ResourceSort.DEFAULT),
            sortAll = sortFromStore("all", ResourceSort.DEFAULT),
        ),
    )

    private val homePollFlow = tickerFlow(LivePoll.HOME_MS, emitImmediately = false)
        .onEach {
            if (sessionStore.session.value != null && !_ui.value.loading && !_ui.value.refreshing) {
                silentRefresh()
            }
        }

    private val logPollFlow = tickerFlow(8_000L, emitImmediately = true)
        .onEach {
            if (sessionStore.session.value != null) {
                repository.logPoll(max = 5)
            }
        }

    val ui: StateFlow<HomeUiState> = merge(
        _ui,
        homePollFlow.map { _ui.value },
        logPollFlow.map { _ui.value },
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _ui.value,
    )

    val latestLog = repository.latestLog

    val profiles: StateFlow<List<SavedProfile>> = sessionStore.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionStore.listProfiles())

    val themeMode: StateFlow<ThemeMode> = sessionStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionStore.themeMode.value)

    fun hasShownVersionToast(): Boolean = sessionStore.hasShownVersionToast()

    fun markVersionToastShown() {
        sessionStore.markVersionToastShown()
    }

    init {
        viewModelScope.launch {
            sessionStore.session.collect { s ->
                _ui.update { it.copy(session = s) }
            }
        }
        refresh(initial = true)
    }

    private fun sortFromStore(key: String, default: ResourceSort): ResourceSort {
        val raw = sessionStore.loadSort(key, default.name)
        return ResourceSort.entries.find { it.name == raw } ?: default
    }

    /** Refresh metrics without spinners or error flash. */
    private suspend fun silentRefresh() {
        repository.listResources().onSuccess { list ->
            _ui.update { it.copy(resources = list) }
        }
    }

    fun setFilter(filter: ResourceFilter) {
        _ui.update { it.copy(filter = filter) }
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
    }

    /** Apply sort for the *current* tab only — other tabs keep their own mode. */
    fun setSort(sort: ResourceSort) {
        _ui.update { state ->
            when (state.filter) {
                ResourceFilter.GUESTS -> {
                    sessionStore.saveSort("guests", sort.name)
                    state.copy(sortGuests = sort)
                }
                ResourceFilter.STORAGE -> {
                    sessionStore.saveSort("storage", sort.name)
                    state.copy(sortStorage = sort)
                }
                ResourceFilter.NODES -> {
                    sessionStore.saveSort("nodes", sort.name)
                    state.copy(sortNodes = sort)
                }
                ResourceFilter.ALL -> {
                    sessionStore.saveSort("all", sort.name)
                    state.copy(sortAll = sort)
                }
            }
        }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = initial,
                    refreshing = !initial,
                    error = null,
                )
            }
            coroutineScope {
                val siteDef = async { repository.siteInfo() }
                val listDef = async { repository.listResources() }
                val site = siteDef.await().getOrNull()
                val listResult = listDef.await()
                listResult.fold(
                    onSuccess = { list ->
                        _ui.update {
                            it.copy(
                                site = site ?: it.site,
                                resources = list,
                                loading = false,
                                refreshing = false,
                                error = null,
                            )
                        }
                    },
                    onFailure = { e ->
                        _ui.update {
                            it.copy(
                                site = site ?: it.site,
                                loading = false,
                                refreshing = false,
                                error = e.message ?: "Failed to load resources",
                            )
                        }
                    },
                )
            }
        }
    }

    fun logout() {
        repository.logout(rememberAsPrevious = true)
    }

    fun setThemeMode(mode: ThemeMode) {
        sessionStore.setThemeMode(mode)
        _ui.update { it.copy(showThemePicker = false) }
    }

    fun showAccounts(show: Boolean) {
        _ui.update { it.copy(showAccounts = show) }
    }

    fun showThemePicker(show: Boolean) {
        _ui.update { it.copy(showThemePicker = show) }
    }

    fun showDeployDialog(show: Boolean) {
        _ui.update { it.copy(showDeployDialog = show, error = if (show) null else it.error) }
    }

    fun triggerDeploy(source: ClusterResource, newId: Long, name: String) {
        AppToast.DEPLOY_STARTED.show(context, source.displayName)
        viewModelScope.launch {
            _ui.update { it.copy(deploying = true, error = null) }
            repository.deployFromTemplate(source, newId, name).fold(
                onSuccess = { upid ->
                    _ui.update { it.copy(deploying = false, showDeployDialog = false, message = "Deploying $name (VMID $newId)") }
                    AppToast.DEPLOY_CREATED.show(context, name, newId)
                    refresh()
                },
                onFailure = { e ->
                    _ui.update { it.copy(deploying = false, error = e.message ?: "Deploy failed") }
                }
            )
        }
    }

    fun quickPowerToggle(resource: ClusterResource, wantOn: Boolean) {
        val guestType = GuestType.fromResourceType(resource.type) ?: return
        val node = resource.node ?: return
        val vmid = resource.vmid ?: return
        val id = resource.id ?: return
        val running = resource.isRunning
        if (wantOn == running) return

        val action = if (wantOn) GuestAction.START else GuestAction.SHUTDOWN
        patchGuest(id) { it.copy(status = if (wantOn) "running" else "stopped") }
        runGuestAction(id, node, guestType, vmid, action, resource.displayName)
    }

    fun quickReboot(resource: ClusterResource) {
        if (!resource.isRunning) return
        val guestType = GuestType.fromResourceType(resource.type) ?: return
        val node = resource.node ?: return
        val vmid = resource.vmid ?: return
        val id = resource.id ?: return
        runGuestAction(id, node, guestType, vmid, GuestAction.REBOOT, resource.displayName)
    }

    fun triggerGuestAction(resource: ClusterResource, action: GuestAction) {
        val guestType = GuestType.fromResourceType(resource.type) ?: return
        val node = resource.node ?: return
        val vmid = resource.vmid ?: return
        val id = resource.id ?: return

        if (action == GuestAction.START) {
            patchGuest(id) { it.copy(status = "running") }
        } else if (action == GuestAction.STOP || action == GuestAction.SHUTDOWN) {
            patchGuest(id) { it.copy(status = "stopped") }
        }

        runGuestAction(id, node, guestType, vmid, action, resource.displayName)
    }

    fun quickOnbootToggle(resource: ClusterResource, enabled: Boolean) {
        val guestType = GuestType.fromResourceType(resource.type) ?: return
        val node = resource.node ?: return
        val vmid = resource.vmid ?: return
        val id = resource.id ?: return
        if (resource.template == 1) return

        patchGuest(id) { it.copy(onboot = if (enabled) 1 else 0) }
        markBusy(id, true)
        viewModelScope.launch {
            repository.setGuestOnboot(node, guestType, vmid, enabled).fold(
                onSuccess = {
                    markBusy(id, false)
                    _ui.update {
                        it.copy(message = "${resource.displayName}: boot ${if (enabled) "on" else "off"}")
                    }
                    if (enabled) {
                        AppToast.AUTOSTART_ENABLED.show(context, resource.displayName)
                    } else {
                        AppToast.AUTOSTART_DISABLED.show(context, resource.displayName)
                    }
                },
                onFailure = { e ->
                    markBusy(id, false)
                    patchGuest(id) { it.copy(onboot = if (enabled) 0 else 1) }
                    _ui.update { it.copy(error = e.message ?: "On-boot update failed") }
                },
            )
        }
    }

    fun triggerBackup(resource: ClusterResource, storage: String, mode: String) {
        val node = resource.node ?: return
        val vmid = resource.vmid ?: return
        val id = resource.id ?: return
        markBusy(id, true)
        AppToast.BACKUP_SERVER_STARTED.show(context)
        viewModelScope.launch {
            repository.createBackup(node, vmid, storage, mode).fold(
                onSuccess = {
                    markBusy(id, false)
                    _ui.update { it.copy(message = "Backup started on server") }
                },
                onFailure = { e ->
                    markBusy(id, false)
                    _ui.update { it.copy(error = e.message ?: "Backup failed") }
                }
            )
        }
    }

    fun triggerBackupToDevice(resource: ClusterResource, storage: String) {
        val node = resource.node ?: return
        val vmid = resource.vmid ?: return
        val id = resource.id ?: return
        val type = resource.type ?: return
        markBusy(id, true)
        AppToast.BACKUP_DEVICE_STARTED.show(context)
        viewModelScope.launch {
            repository.backupToDevice(node, type, vmid, storage) { progress ->
                _ui.update { it.copy(message = progress) }
            }.fold(
                onSuccess = { filename ->
                    markBusy(id, false)
                    _ui.update { it.copy(message = "Saved $filename to Downloads") }
                    AppToast.BACKUP_SAVED.show(context)
                },
                onFailure = { e ->
                    markBusy(id, false)
                    _ui.update { it.copy(error = e.message ?: "Download failed") }
                    AppToast.BACKUP_FAILED.show(context, e.message ?: "Download failed")
                }
            )
        }
    }

    /** Helper to get storage for a node (for the backup dialog). */
    suspend fun getNodeStorage(node: String): List<String> {
        return repository.listNodeStorageNames(node).getOrDefault(emptyList())
    }

    private fun runGuestAction(
        id: String,
        node: String,
        guestType: GuestType,
        vmid: Long,
        action: GuestAction,
        guestName: String = "Guest",
    ) {
        markBusy(id, true)
        viewModelScope.launch {
            repository.guestAction(node, guestType, vmid, action).fold(
                onSuccess = {
                    markBusy(id, false)
                    _ui.update { it.copy(message = "${action.label} started") }
                    showGuestActionToast(action, guestName)
                    refresh()
                },
                onFailure = { e ->
                    markBusy(id, false)
                    _ui.update { it.copy(error = e.message ?: "Action failed") }
                    AppToast.ACTION_FAILED.show(context, action.label, e.message ?: "Failed")
                    refresh()
                },
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

    private fun markBusy(id: String, busy: Boolean) {
        _ui.update { state ->
            state.copy(
                busyGuestIds = if (busy) state.busyGuestIds + id else state.busyGuestIds - id,
            )
        }
    }

    private fun patchGuest(id: String, transform: (ClusterResource) -> ClusterResource) {
        _ui.update { state ->
            state.copy(
                resources = state.resources.map { r ->
                    if (r.id == id) transform(r) else r
                },
            )
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val sessionStore: SessionStore,
        private val context: Context = repository.appContext,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, sessionStore, context) as T
        }
    }

}
