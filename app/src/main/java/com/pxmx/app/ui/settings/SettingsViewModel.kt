package com.pxmx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.data.model.FirewallSnapshot
import com.pxmx.app.data.model.NodeUpdateSnapshot
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.ui.util.tickerFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

data class SettingsUiState(
    val firewallSubtitle: String = "Datacenter and node rules · enable toggle",
    val sdnSubtitle: String = "Software-defined zones, vnets, and status · apply",
    val logsSubtitle: String = "Cluster syslog and event feed",
    val updatesSubtitle: String = "Pending packages with live apt job status",
    val firewallSnapshot: FirewallSnapshot? = null,
    val sdnZones: List<SdnZoneInfo>? = null,
    val sdnVnets: List<SdnVnetInfo>? = null,
    val logEntries: List<ClusterLogEntry>? = null,
    val updateSnapshots: List<NodeUpdateSnapshot>? = null,
)

class SettingsViewModel(
    private val repository: ProxmoxRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope
    private val _ui = MutableStateFlow(SettingsUiState())

    private val pollFlow = tickerFlow(6500L, emitImmediately = true)
        .onEach {
            pollCheap()
        }

    val ui: StateFlow<SettingsUiState> = merge(
        _ui,
        pollFlow.map { _ui.value },
    ).stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _ui.value,
    )

    val state: SettingsUiState get() = _ui.value

    init {
        loadAll()
    }

    fun refresh() {
        loadAll()
    }

    private fun loadAll() {
        // Cheap GETs in parallel
        scope.launch {
            pollCheap()
        }
        // Updates async: do not block cheap GETs if apt is slow
        scope.launch {
            loadUpdates()
        }
    }

    suspend fun pollCheap() {
        try {
            coroutineScope {
                val fwDef = async { repository.loadClusterFirewall() }
                val zonesDef = async { repository.listSdnZones() }
                val vnetsDef = async { repository.listSdnVnets() }
                val logsDef = async { repository.logHistory(max = 20) }

                val fwRes = fwDef.await()
                val zonesRes = zonesDef.await()
                val vnetsRes = vnetsDef.await()
                val logsRes = logsDef.await()

                val fwSub = formatFirewallSubtitle(fwRes)
                val sdnSub = formatSdnSubtitle(zonesRes, vnetsRes)
                val logsSub = formatLogsSubtitle(logsRes)

                _ui.update {
                    it.copy(
                        firewallSubtitle = fwSub,
                        sdnSubtitle = sdnSub,
                        logsSubtitle = logsSub,
                        firewallSnapshot = fwRes.getOrNull(),
                        sdnZones = zonesRes.getOrNull(),
                        sdnVnets = vnetsRes.getOrNull(),
                        logEntries = logsRes.getOrNull(),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Individual failures are handled inside format helpers
        }
    }

    suspend fun loadUpdates() {
        try {
            val updatesRes = repository.listClusterUpdates()
            val updatesSub = formatUpdatesSubtitle(updatesRes)
            _ui.update {
                it.copy(
                    updatesSubtitle = updatesSub,
                    updateSnapshots = updatesRes.getOrNull(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Failures handled in format helper
        }
    }

    class Factory(private val repository: ProxmoxRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }

    companion object {
        fun formatFirewallSubtitle(result: Result<FirewallSnapshot>): String {
            return result.fold(
                onSuccess = { snapshot ->
                    val status = if (snapshot.enabled) "enabled" else "disabled"
                    val count = snapshot.rules.size
                    val ruleWord = if (count == 1) "rule" else "rules"
                    "$status · $count $ruleWord"
                },
                onFailure = { e ->
                    val msg = e.message.orEmpty().lowercase()
                    if (msg.contains("403") || msg.contains("forbidden")) {
                        "permission required"
                    } else {
                        "firewall unavailable"
                    }
                }
            )
        }

        fun formatSdnSubtitle(
            zonesResult: Result<List<SdnZoneInfo>>,
            vnetsResult: Result<List<SdnVnetInfo>>,
        ): String {
            val err = zonesResult.exceptionOrNull() ?: vnetsResult.exceptionOrNull()
            if (err != null) {
                val msg = err.message.orEmpty().lowercase()
                return when {
                    msg.contains("501") || msg.contains("not implemented") || msg.contains("not configured") -> "not configured"
                    msg.contains("403") || msg.contains("forbidden") -> "permission required"
                    else -> "sdn unavailable"
                }
            }
            val zones = zonesResult.getOrDefault(emptyList())
            val vnets = vnetsResult.getOrDefault(emptyList())
            val zWord = if (zones.size == 1) "zone" else "zones"
            val vWord = if (vnets.size == 1) "vnet" else "vnets"
            return "${zones.size} $zWord · ${vnets.size} $vWord"
        }

        fun formatLogsSubtitle(result: Result<List<ClusterLogEntry>>): String {
            return result.fold(
                onSuccess = { logs ->
                    val count = logs.size
                    val word = if (count == 1) "event" else "events"
                    "$count $word"
                },
                onFailure = { e ->
                    val msg = e.message.orEmpty().lowercase()
                    if (msg.contains("403") || msg.contains("forbidden")) {
                        "permission required"
                    } else {
                        "logs unavailable"
                    }
                }
            )
        }

        fun formatUpdatesSubtitle(result: Result<List<NodeUpdateSnapshot>>): String {
            return result.fold(
                onSuccess = { snapshots ->
                    val total = snapshots.sumOf { it.updateCount }
                    "$total pending"
                },
                onFailure = { e ->
                    val msg = e.message.orEmpty().lowercase()
                    if (msg.contains("403") || msg.contains("forbidden")) {
                        "permission required"
                    } else {
                        "updates unavailable"
                    }
                }
            )
        }
    }
}
