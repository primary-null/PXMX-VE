package com.pxmx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.ui.util.tickerFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SdnUiState(
    val zones: List<SdnZoneInfo> = emptyList(),
    val vnets: List<SdnVnetInfo> = emptyList(),
    val statuses: List<SdnStatusInfo> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isApplying: Boolean = false,
    val actionError: String? = null,
    val jobStatus: String? = null,
    val taskLogLines: List<String> = emptyList(),
) {
    val issueStatuses: List<SdnStatusInfo>
        get() = statuses.filter { !it.isOk }
}

class SdnViewModel(
    private val repository: ProxmoxRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SdnUiState())

    private val pollFlow = tickerFlow(6500L, emitImmediately = false)
        .onEach {
            pollRefresh()
        }

    val ui: StateFlow<SdnUiState> = merge(
        _ui,
        pollFlow.map { _ui.value },
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _ui.value,
    )

    init {
        refresh(initial = true)
    }

    fun clearActionError() {
        _ui.update { it.copy(actionError = null) }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = initial && it.zones.isEmpty() && it.vnets.isEmpty(),
                    refreshing = !initial,
                    error = null,
                )
            }
            fetchData()
        }
    }

    private suspend fun pollRefresh() {
        if (_ui.value.isApplying) return
        fetchData()
    }

    private suspend fun fetchData() {
        try {
            coroutineScope {
                val zonesDef = async { repository.listSdnZones() }
                val vnetsDef = async { repository.listSdnVnets() }
                val statusDef = async { repository.listSdnStatus() }

                val zonesRes = zonesDef.await()
                val vnetsRes = vnetsDef.await()
                val statusRes = statusDef.await()

                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        zones = zonesRes.getOrDefault(emptyList()),
                        vnets = vnetsRes.getOrDefault(emptyList()),
                        statuses = statusRes.getOrDefault(emptyList()),
                        error = zonesRes.exceptionOrNull()?.message
                            ?: vnetsRes.exceptionOrNull()?.message
                            ?: statusRes.exceptionOrNull()?.message,
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
                    error = e.message ?: "Failed to load SDN configuration",
                )
            }
        }
    }

    fun applySdn() {
        if (_ui.value.isApplying) return

        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isApplying = true,
                    actionError = null,
                    jobStatus = "APPLYING SDN CONFIGURATION",
                    taskLogLines = emptyList(),
                )
            }

            var taskUpid: String? = null

            try {
                val applyRes = repository.applySdn()
                applyRes.fold(
                    onSuccess = { upidOrOk ->
                        if (upidOrOk.startsWith("UPID:")) {
                            taskUpid = upidOrOk
                            val parts = upidOrOk.split(":")
                            val taskNode = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "cluster"

                            repository.setActiveAptTask(taskNode, upidOrOk, "sdnreload")

                            val awaitDef = async {
                                repository.awaitTask(taskNode, upidOrOk, timeoutMs = 60_000)
                            }

                            while (!awaitDef.isCompleted) {
                                val logLines = repository.taskLog(taskNode, upidOrOk, limit = 10)
                                    .getOrDefault(emptyList())
                                if (logLines.isNotEmpty()) {
                                    _ui.update { it.copy(taskLogLines = logLines) }
                                }
                                delay(600L)
                            }

                            val taskOutcome = awaitDef.await()
                            taskOutcome.fold(
                                onSuccess = { status ->
                                    if (!status.isOk) {
                                        val err = status.exitstatus ?: status.status ?: "SDN apply task failed"
                                        _ui.update { it.copy(actionError = err) }
                                    }
                                },
                                onFailure = { e ->
                                    _ui.update { it.copy(actionError = e.message ?: "SDN task failed") }
                                }
                            )
                        }
                    },
                    onFailure = { e ->
                        _ui.update {
                            it.copy(actionError = e.message ?: "Failed to apply SDN")
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(actionError = e.message ?: "Failed to apply SDN")
                }
            } finally {
                repository.clearActiveAptTask(taskUpid)
                // Reload zones/vnets/status and show the NEW status
                fetchData()
                _ui.update { it.copy(isApplying = false, jobStatus = null) }
            }
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SdnViewModel(repository) as T
        }
    }
}
