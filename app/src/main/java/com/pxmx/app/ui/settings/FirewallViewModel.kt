package com.pxmx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.ui.util.tickerFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirewallUiState(
    val selectedTarget: String = "cluster", // "cluster" or node name
    val nodeNames: List<String> = emptyList(),
    val cluster: FirewallSnapshot? = null,
    val nodeSnapshots: Map<String, FirewallSnapshot> = emptyMap(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isApplying: Boolean = false,
    val actionError: String? = null,
) {
    val currentSnapshot: FirewallSnapshot?
        get() = if (selectedTarget == "cluster") cluster else nodeSnapshots[selectedTarget]
}

class FirewallViewModel(
    private val repository: ProxmoxRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val _ui = MutableStateFlow(FirewallUiState())

    private val pollFlow = tickerFlow(6500L, emitImmediately = false)
        .onEach {
            pollRefresh()
        }

    val ui: StateFlow<FirewallUiState> = merge(
        _ui,
        pollFlow.map { _ui.value },
    ).stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _ui.value,
    )

    val state: FirewallUiState get() = _ui.value

    init {
        refresh(initial = true)
    }

    fun selectTarget(target: String) {
        _ui.update { it.copy(selectedTarget = target, actionError = null) }
    }

    fun clearActionError() {
        _ui.update { it.copy(actionError = null) }
    }

    fun refresh(initial: Boolean = false): Job = scope.launch {
        _ui.update {
            it.copy(
                loading = initial && it.cluster == null,
                refreshing = !initial,
                error = null,
            )
        }
        fetchData()
    }

    private suspend fun pollRefresh() {
        if (_ui.value.isApplying) return
        fetchData()
    }

    internal suspend fun fetchData() {
        try {
            coroutineScope {
                val clusterDef = async { repository.loadClusterFirewall() }
                val nodesDef = async { repository.listNodeNames() }
                val clusterRes = clusterDef.await()
                val nodeNames = nodesDef.await().getOrDefault(emptyList())

                val nodeSnaps = nodeNames.associateWith { node ->
                    repository.loadNodeFirewall(node).getOrNull()
                }.filterValues { it != null }.mapValues { it.value!! }

                _ui.update {
                    it.copy(
                        cluster = clusterRes.getOrNull() ?: it.cluster,
                        nodeNames = if (nodeNames.isNotEmpty()) nodeNames else it.nodeNames,
                        nodeSnapshots = if (nodeSnaps.isNotEmpty()) nodeSnaps else it.nodeSnapshots,
                        loading = false,
                        refreshing = false,
                        error = clusterRes.exceptionOrNull()?.message,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Failed to load firewall options",
                )
            }
        }
    }

    fun setFirewallEnabled(enable: Boolean): Job? {
        val target = _ui.value.selectedTarget
        val currentSnap = _ui.value.currentSnapshot
        val digest = currentSnap?.options?.get("digest")?.toString()

        if (enable && target == "cluster") {
            val has8006Accept = currentSnap?.hasInbound8006Accept == true
            if (!has8006Accept) {
                _ui.update {
                    it.copy(
                        isApplying = false,
                        actionError = REFUSAL_EMPTY_FIREWALL_MESSAGE,
                    )
                }
                return null
            }
        }

        return scope.launch {
            _ui.update { it.copy(isApplying = true, actionError = null) }
            try {
                val result = if (target == "cluster") {
                    repository.setClusterFirewallEnable(enable, digest)
                } else {
                    repository.setNodeFirewallEnable(target, enable, digest)
                }

                result.fold(
                    onSuccess = {
                        // Immediately reload snapshot and show the NEW enable/policy
                        if (target == "cluster") {
                            val reloaded = repository.loadClusterFirewall()
                            _ui.update {
                                it.copy(
                                    isApplying = false,
                                    actionError = null,
                                    cluster = reloaded.getOrNull() ?: it.cluster,
                                    error = reloaded.exceptionOrNull()?.message,
                                )
                            }
                        } else {
                            val reloaded = repository.loadNodeFirewall(target)
                            _ui.update {
                                val updatedSnaps = it.nodeSnapshots.toMutableMap()
                                reloaded.getOrNull()?.let { snap -> updatedSnaps[target] = snap }
                                it.copy(
                                    isApplying = false,
                                    actionError = null,
                                    nodeSnapshots = updatedSnaps,
                                    error = reloaded.exceptionOrNull()?.message,
                                )
                            }
                        }
                    },
                    onFailure = { e ->
                        _ui.update {
                            it.copy(
                                isApplying = false,
                                actionError = e.message ?: "Failed to update firewall",
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        isApplying = false,
                        actionError = e.message ?: "Failed to update firewall",
                    )
                }
            }
        }
    }

    companion object {
        const val REFUSAL_EMPTY_FIREWALL_MESSAGE =
            "PXMX will not enable an empty datacenter firewall. Add an ACCEPT rule for 8006 first."
    }

    class Factory(
        private val repository: ProxmoxRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FirewallViewModel(repository) as T
        }
    }
}
