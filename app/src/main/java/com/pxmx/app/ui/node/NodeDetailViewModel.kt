package com.pxmx.app.ui.node

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.LivePoll
import com.pxmx.app.data.model.NodeServiceInfo
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeTaskInfo
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.ui.util.tickerFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NodeDetailUiState(
    val node: String,
    val status: NodeStatus? = null,
    val services: List<NodeServiceInfo> = emptyList(),
    val tasks: List<NodeTaskInfo> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class NodeDetailViewModel(
    private val repository: ProxmoxRepository,
    node: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NodeDetailUiState(node = node))

    private val nodePollingFlow = tickerFlow(LivePoll.NODE_MS, emitImmediately = false)
        .onEach {
            if (!_uiState.value.loading && !_uiState.value.refreshing) {
                // Silent live metrics for node status only
                repository.loadNodeBundle(_uiState.value.node).onSuccess { bundle ->
                    _uiState.update {
                        it.copy(
                            status = bundle.status,
                            services = bundle.services,
                            tasks = bundle.tasks,
                        )
                    }
                }
            }
        }

    val ui: StateFlow<NodeDetailUiState> = merge(
        _uiState,
        nodePollingFlow.map { _uiState.value },
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        val node = _uiState.value.node
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = initial && it.status == null,
                    refreshing = !initial,
                    error = null,
                )
            }
            repository.loadNodeBundle(node).fold(
                onSuccess = { bundle ->
                    _uiState.update {
                        it.copy(
                            status = bundle.status,
                            services = bundle.services,
                            tasks = bundle.tasks,
                            loading = false,
                            refreshing = false,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = e.message ?: "Failed to load node",
                        )
                    }
                },
            )
        }
    }

    fun getBrowserUrl(): String? {
        val s = repository.sessionStore.session.value
        return s?.config?.let { "https://${it.displayHost}" }
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val node: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NodeDetailViewModel(repository, node) as T
        }
    }
}
