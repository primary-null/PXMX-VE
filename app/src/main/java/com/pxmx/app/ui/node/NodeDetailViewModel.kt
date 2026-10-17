package com.pxmx.app.ui.node

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.LivePoll
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.HistoryTimeframe
import com.pxmx.app.data.model.NodePowerCommand
import com.pxmx.app.data.model.NodeServiceInfo
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeTaskInfo
import com.pxmx.app.data.model.RrdPoint
import com.pxmx.app.data.repo.NodeRepository
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import com.pxmx.app.ui.util.AppToast
import com.pxmx.app.ui.util.tickerFlow
import kotlinx.coroutines.CancellationException
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
    val isTokenSession: Boolean = false,
    val rrdData: List<RrdPoint> = emptyList(),
    val rrdTimeframe: HistoryTimeframe = HistoryTimeframe.HOUR,
    val actionInProgress: String? = null,
)

class NodeDetailViewModel(
    private val nodeRepo: NodeRepository,
    private val sessionStore: SessionStore,
    node: String,
    private val context: Context? = null,
) : ViewModel() {

    val watchedIds: StateFlow<Set<String>> = sessionStore.watchedIds

    fun setWatched(id: String, watched: Boolean) {
        sessionStore.setWatched(id, watched)
    }

    private val _uiState = MutableStateFlow(
        NodeDetailUiState(
            node = node,
            isTokenSession = sessionStore.session.value?.config?.authMode == AuthMode.API_TOKEN,
        ),
    )

    private val nodePollingFlow = tickerFlow(LivePoll.NODE_MS, emitImmediately = false)
        .onEach {
            if (!_uiState.value.loading && !_uiState.value.refreshing) {
                // Silent live metrics for node status only
                nodeRepo.loadNodeBundle(_uiState.value.node).onSuccess { bundle ->
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
            val tf = _uiState.value.rrdTimeframe
            val rrdResult = nodeRepo.nodeRrdData(node, tf.apiName)
            nodeRepo.loadNodeBundle(node).fold(
                onSuccess = { bundle ->
                    _uiState.update {
                        it.copy(
                            status = bundle.status,
                            services = bundle.services,
                            tasks = bundle.tasks,
                            rrdData = if (it.rrdTimeframe == tf) {
                                rrdResult.getOrDefault(it.rrdData)
                            } else {
                                it.rrdData
                            },
                            loading = false,
                            refreshing = false,
                        )
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
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

    fun setRrdTimeframe(timeframe: HistoryTimeframe) {
        if (_uiState.value.rrdTimeframe == timeframe) return
        val previous = _uiState.value.rrdTimeframe
        _uiState.update { it.copy(rrdTimeframe = timeframe) }
        val node = _uiState.value.node
        viewModelScope.launch {
            nodeRepo.nodeRrdData(node, timeframe.apiName).fold(
                onSuccess = { points ->
                    _uiState.update {
                        if (it.rrdTimeframe == timeframe) {
                            it.copy(rrdData = points, error = null)
                        } else {
                            it
                        }
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
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

    fun getBrowserUrl(): String? {
        val s = sessionStore.session.value
        return s?.config?.let { "https://${it.displayHost}" }
    }

    fun runPowerCommand(command: NodePowerCommand) {
        if (_uiState.value.actionInProgress != null) return
        val nodeName = _uiState.value.node
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = command.apiName, error = null) }
            nodeRepo.nodeCommand(nodeName, command).fold(
                onSuccess = {
                    val ctx = context
                    if (ctx != null) {
                        when (command) {
                            NodePowerCommand.REBOOT -> AppToast.NODE_REBOOTED.show(ctx, nodeName)
                            NodePowerCommand.SHUTDOWN -> AppToast.NODE_SHUTDOWN.show(ctx, nodeName)
                        }
                    }
                    _uiState.update { it.copy(actionInProgress = null) }
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    context?.let { AppToast.ACTION_FAILED.show(it, command.label, e.message ?: "Failed") }
                    _uiState.update {
                        it.copy(
                            actionInProgress = null,
                            error = e.message ?: "Failed to ${command.apiName} node",
                        )
                    }
                },
            )
        }
    }

    constructor(
        repository: ProxmoxRepository,
        node: String,
    ) : this(
        nodeRepo = repository.nodeRepo,
        sessionStore = repository.sessionStore,
        node = node,
        context = repository.appContext,
    )

    class Factory(
        private val repository: ProxmoxRepository,
        private val node: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NodeDetailViewModel(
                nodeRepo = repository.nodeRepo,
                sessionStore = repository.sessionStore,
                node = node,
                context = repository.appContext,
            ) as T
        }
    }
}
