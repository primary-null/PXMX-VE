package com.pxmx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.repo.ProxmoxRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val currentSnapshot: FirewallSnapshot?
        get() = if (selectedTarget == "cluster") cluster else nodeSnapshots[selectedTarget]
}

class FirewallViewModel(
    private val repository: ProxmoxRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FirewallUiState())
    val ui: StateFlow<FirewallUiState> = _ui.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun selectTarget(target: String) {
        _ui.update { it.copy(selectedTarget = target) }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = initial && it.cluster == null,
                    refreshing = !initial,
                    error = null,
                )
            }
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
                        cluster = clusterRes.getOrNull(),
                        nodeNames = nodeNames,
                        nodeSnapshots = nodeSnaps,
                        loading = false,
                        refreshing = false,
                        error = clusterRes.exceptionOrNull()?.message,
                    )
                }
            }
        }
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
