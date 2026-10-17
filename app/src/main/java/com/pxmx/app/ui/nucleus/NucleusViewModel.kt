package com.pxmx.app.ui.nucleus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.config.GuestConfigParser
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.SiteInfo
import com.pxmx.app.data.repo.ProxmoxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class NucleusUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sessionLost: Boolean = false,
    val siteTitle: String = "Proxmox",
    val subtitle: String = "STANDALONE · 0 RUNNING GUESTS",
    val layout: NucleusSceneLayout = buildNucleusLayout(null, emptyList()),
    val selectedGuestKey: String? = null,
    val selectedSpokes: List<NucleusSpoke> = emptyList(),
    val loadingSpokes: Boolean = false,
    val error: String? = null,
)

class NucleusViewModel(
    private val siteInfoProvider: suspend () -> Result<SiteInfo>,
    private val resourcesProvider: suspend () -> Result<List<ClusterResource>>,
    private val rawConfigFetcher: suspend (node: String, type: String, vmid: Long) -> Result<Map<String, Any>>,
    private val hasActiveSession: () -> Boolean = { true },
) : ViewModel() {

    constructor(repository: ProxmoxRepository) : this(
        siteInfoProvider = { repository.siteInfo() },
        resourcesProvider = { repository.listResources() },
        rawConfigFetcher = { node, type, vmid ->
            repository.guestRepo.fetchRawGuestConfig(node, type, vmid)
        },
        hasActiveSession = { repository.sessionStore.session.value != null },
    )

    private val _ui = MutableStateFlow(NucleusUiState())
    val ui: StateFlow<NucleusUiState> = _ui.asStateFlow()

    /** Successful config fetches cached per guest key so each guest config is fetched at most once. */
    private val spokeCache = ConcurrentHashMap<String, List<NucleusSpoke>>()
    private var fetchJob: Job? = null

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            if (!hasActiveSession()) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        sessionLost = true,
                        selectedGuestKey = null,
                        selectedSpokes = emptyList(),
                        loadingSpokes = false,
                        error = null,
                    )
                }
                return@launch
            }

            _ui.update {
                it.copy(
                    loading = initial && it.layout.guests.isEmpty() && it.layout.nodes.isEmpty(),
                    refreshing = !initial,
                    sessionLost = false,
                    error = null,
                )
            }

            try {
                val (siteRes, resourcesRes) = coroutineScope {
                    val sDef = async { siteInfoProvider() }
                    val rDef = async { resourcesProvider() }
                    sDef.await() to rDef.await()
                }

                siteRes.exceptionOrNull()?.let { e ->
                    if (e is CancellationException) throw e
                }
                resourcesRes.exceptionOrNull()?.let { e ->
                    if (e is CancellationException) throw e
                }

                val siteErr = siteRes.exceptionOrNull()
                val resErr = resourcesRes.exceptionOrNull()
                val isAuthLoss = !hasActiveSession() ||
                    siteErr?.message?.contains("Not logged in", ignoreCase = true) == true ||
                    resErr?.message?.contains("Not logged in", ignoreCase = true) == true

                if (isAuthLoss && siteRes.isFailure && resourcesRes.isFailure) {
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            sessionLost = true,
                            selectedGuestKey = null,
                            selectedSpokes = emptyList(),
                            loadingSpokes = false,
                            error = null,
                        )
                    }
                    return@launch
                }

                val site = siteRes.getOrNull()
                val resources = resourcesRes.getOrElse { emptyList() }
                val scene = buildNucleusLayout(site, resources)

                _ui.update { prev ->
                    val stillSelected = prev.selectedGuestKey?.takeIf { key ->
                        scene.guests.any { g -> g.key == key }
                    }
                    prev.copy(
                        loading = false,
                        refreshing = false,
                        sessionLost = false,
                        siteTitle = scene.core.label,
                        subtitle = scene.subtitle,
                        layout = scene,
                        selectedGuestKey = stillSelected,
                        selectedSpokes = if (stillSelected != null) prev.selectedSpokes else emptyList(),
                        loadingSpokes = if (stillSelected != null) prev.loadingSpokes else false,
                        error = if (siteRes.isFailure && resourcesRes.isFailure) {
                            resErr?.message ?: siteErr?.message
                        } else {
                            null
                        },
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message,
                    )
                }
            }
        }
    }

    fun onGuestTapped(guest: NucleusGuest) {
        val currentKey = _ui.value.selectedGuestKey
        if (currentKey == guest.key) {
            fetchJob?.cancel()
            fetchJob = null
            _ui.update {
                it.copy(
                    selectedGuestKey = null,
                    selectedSpokes = emptyList(),
                    loadingSpokes = false,
                )
            }
            return
        }

        fetchJob?.cancel()
        val cached = spokeCache[guest.key]
        if (cached != null) {
            _ui.update {
                it.copy(
                    selectedGuestKey = guest.key,
                    selectedSpokes = cached,
                    loadingSpokes = false,
                )
            }
            return
        }

        _ui.update {
            it.copy(
                selectedGuestKey = guest.key,
                selectedSpokes = emptyList(),
                loadingSpokes = true,
            )
        }

        fetchJob = viewModelScope.launch {
            try {
                val res = rawConfigFetcher(guest.node, guest.type, guest.vmid)
                res.exceptionOrNull()?.let { e ->
                    if (e is CancellationException) throw e
                }
                val rawMap = res.getOrNull()
                if (rawMap != null) {
                    val parsed = GuestConfigParser.parse(rawMap)
                    val spokes = buildGuestSpokes(parsed)
                    spokeCache[guest.key] = spokes
                    _ui.update { state ->
                        if (state.selectedGuestKey == guest.key) {
                            state.copy(selectedSpokes = spokes, loadingSpokes = false)
                        } else {
                            state
                        }
                    }
                } else {
                    // Failed fetch: show "Config unavailable" and do NOT cache so a later tap can retry.
                    _ui.update { state ->
                        if (state.selectedGuestKey == guest.key) {
                            state.copy(
                                selectedSpokes = buildUnavailableSpokes(),
                                loadingSpokes = false,
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { state ->
                    if (state.selectedGuestKey == guest.key) {
                        state.copy(
                            selectedSpokes = buildUnavailableSpokes(),
                            loadingSpokes = false,
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NucleusViewModel(repository) as T
        }
    }
}
