package com.pxmx.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.repo.ProxmoxRepository
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

data class LogUiState(
    val logs: List<ClusterLogEntry> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class LogViewModel(
    private val repository: ProxmoxRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())

    private val logPollingFlow = tickerFlow(6500L, emitImmediately = true)
        .onEach {
            fetchLogs(isInitial = _uiState.value.logs.isEmpty())
        }

    val ui: StateFlow<LogUiState> = merge(
        _uiState,
        logPollingFlow.map { _uiState.value },
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _uiState.value,
    )

    fun refresh() {
        _uiState.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch { fetchLogs(isInitial = false) }
    }

    private suspend fun fetchLogs(isInitial: Boolean) {
        try {
            repository.logHistory(max = 200).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            logs = list,
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = if (isInitial || it.logs.isEmpty()) e.message ?: "Failed to load cluster log" else it.error,
                        )
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Failed to load cluster log",
                )
            }
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LogViewModel(repository) as T
    }
}
