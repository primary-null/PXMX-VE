package com.pxmx.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.repo.ProxmoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClusterTask(
    val upid: String,
    val node: String,
    val type: String,
    val user: String,
    val status: String,
    val startTime: Long,
    val endTime: Long?,
    val pid: Long?,
)

data class TasksUiState(
    val tasks: List<ClusterTask> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class TasksViewModel(
    private val repository: ProxmoxRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TasksUiState())
    val ui: StateFlow<TasksUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch { fetchTasks() }
    }

    private fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { fetchTasks() }
    }

    private suspend fun fetchTasks() {
        repository.clusterTasks().fold(
            onSuccess = { raw ->
                val tasks = raw.mapNotNull { map ->
                    val upid = map["upid"] as? String ?: return@mapNotNull null
                    ClusterTask(
                        upid = upid,
                        node = map["node"] as? String ?: "—",
                        type = map["type"] as? String ?: "—",
                        user = map["user"] as? String ?: "—",
                        status = map["status"] as? String ?: "running",
                        startTime = (map["starttime"] as? Number)?.toLong() ?: 0L,
                        endTime = (map["endtime"] as? Number)?.toLong(),
                        pid = (map["pid"] as? Number)?.toLong(),
                    )
                }.sortedByDescending { it.startTime }
                _ui.update {
                    it.copy(tasks = tasks, loading = false, refreshing = false, error = null)
                }
            },
            onFailure = { e ->
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "Failed to load tasks",
                    )
                }
            },
        )
    }

    class Factory(
        private val repository: ProxmoxRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasksViewModel(repository) as T
    }
}
