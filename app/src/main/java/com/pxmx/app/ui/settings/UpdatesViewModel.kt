package com.pxmx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.AptPackageUpdate
import com.pxmx.app.data.model.AuthMode
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeUpdateSnapshot
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class NodeRefreshState {
    IDLE,
    PARSING,
    UPGRADING,
    COMPLETE,
    ERROR,
}

enum class SshUpgradeAvailability {
    AVAILABLE,
    NO_SAVED_SECRET,
    API_TOKEN_AUTH,
}

const val PRIVILEGE_DENIED_COPY = "This account lacks package-management privileges on this node. Use an account with Sys.Modify, or run upgrades from the node shell."

data class NodeRefreshProgress(
    val node: String,
    val state: NodeRefreshState = NodeRefreshState.IDLE,
    val activePackageIndex: Int = -1,
    val progressFraction: Float = 0f,
    val detail: String? = null,
    val errorDetail: String? = null,
    val isPrivilegeDenied: Boolean = false,
    val startTimeMs: Long = 0L,
    val elapsedSec: Double = 0.0,
    val completedPackagesCount: Int = 0,
    val securityPackagesCount: Int = 0,
    val remoteUpgradeRemoved: Boolean = false,
    val logLines: List<String> = emptyList(),
)

data class UpdatesUiState(
    val nodes: List<NodeUpdateSnapshot> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val progress: Map<String, NodeRefreshProgress> = emptyMap(),
    val nodeStatuses: Map<String, NodeStatus> = emptyMap(),
    val serverHost: String = "",
    val pveVersion: String = "",
    val pveVersionMajor: Int? = null,
    val sshAvailability: SshUpgradeAvailability = SshUpgradeAvailability.AVAILABLE,
    val remoteUpgradeRemovedNodes: Set<String> = emptySet(),
    val message: String? = null,
    val error: String? = null,
) {
    val isPasswordAuth: Boolean get() = sshAvailability == SshUpgradeAvailability.AVAILABLE
    val totalPending: Int get() = nodes.sumOf { it.updateCount }
    val nodesWithUpdates: Int get() = nodes.count { it.updateCount > 0 }
    val anyJobActive: Boolean
        get() = progress.values.any {
            it.state == NodeRefreshState.PARSING || it.state == NodeRefreshState.UPGRADING
        }

    fun isRemoteUpgradeRemoved(node: String): Boolean =
        remoteUpgradeRemovedNodes.contains(node) || progress[node]?.remoteUpgradeRemoved == true
}

fun isHttp403(e: Throwable?): Boolean {
    if (e == null) return false
    if ((e as? retrofit2.HttpException)?.code() == 403) return true
    if ((e.cause as? retrofit2.HttpException)?.code() == 403) return true
    val msg = e.message ?: return false
    return isHttp403Message(msg)
}

fun isHttp403Message(msg: String?): Boolean {
    if (msg == null) return false
    return msg.contains("403") || msg.contains("permission check failed", ignoreCase = true)
}

private val HTTP_501_WORD_REGEX = Regex("""\b501\b""")

fun isHttp501(e: Throwable?): Boolean {
    if (e == null) return false
    if ((e as? retrofit2.HttpException)?.code() == 501) return true
    if ((e.cause as? retrofit2.HttpException)?.code() == 501) return true
    val msg = e.message ?: return false
    return isHttp501Message(msg)
}

fun isHttp501Message(msg: String?): Boolean {
    if (msg == null) return false
    if (msg.contains("Method not implemented", ignoreCase = true)) return true
    return HTTP_501_WORD_REGEX.containsMatchIn(msg)
}

fun isSecurityUpdate(upd: AptPackageUpdate): Boolean {
    val prio = upd.priority.orEmpty().lowercase(Locale.US)
    val sec = upd.section.orEmpty().lowercase(Locale.US)
    val orig = upd.origin.orEmpty().lowercase(Locale.US)
    return prio == "important" || prio == "required" || sec.contains("security") || orig.contains("security")
}

class UpdatesViewModel(
    private val repository: ProxmoxRepository,
    private val sessionStore: SessionStore = repository.sessionStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        UpdatesUiState(
            serverHost = resolveServerHost(),
            pveVersion = resolvePveVersion(),
            pveVersionMajor = resolvePveVersionMajor(),
            sshAvailability = resolveSshAvailability(),
        ),
    )
    val ui: StateFlow<UpdatesUiState> = _ui.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun setScreenActive(active: Boolean) {
        repository.setUpdatesScreenActive(active)
    }

    private fun resolveServerHost(): String {
        val s = sessionStore.session.value ?: repository.sessionStore.session.value
        val cfg = s?.config ?: return "cluster:8006"
        return if (cfg.host.equals("demo", ignoreCase = true)) "demo:8006"
        else cfg.displayHost
    }

    private fun resolvePveVersion(): String {
        val s = sessionStore.session.value ?: repository.sessionStore.session.value
        return s?.version?.display ?: "PVE 8.x"
    }

    private fun resolvePveVersionMajor(): Int? {
        val s = sessionStore.session.value ?: repository.sessionStore.session.value
        return s?.version?.major
    }

    private fun resolveSshAvailability(): SshUpgradeAvailability {
        val s = sessionStore.session.value ?: repository.sessionStore.session.value
        val cfg = s?.config ?: return SshUpgradeAvailability.NO_SAVED_SECRET
        if (cfg.host.equals("demo", ignoreCase = true)) return SshUpgradeAvailability.AVAILABLE
        val profile = sessionStore.listProfiles().firstOrNull { it.host == cfg.host }
        val authMode = profile?.authMode ?: cfg.authMode
        if (authMode != AuthMode.PASSWORD) {
            return SshUpgradeAvailability.API_TOKEN_AUTH
        }
        val hasSecret = (profile?.hasSavedSecret == true && profile.password.isNotBlank()) || cfg.password.isNotBlank()
        if (!hasSecret) {
            return SshUpgradeAvailability.NO_SAVED_SECRET
        }
        return SshUpgradeAvailability.AVAILABLE
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = initial && it.nodes.isEmpty(),
                    refreshing = !initial,
                    serverHost = resolveServerHost(),
                    pveVersion = resolvePveVersion(),
                    pveVersionMajor = resolvePveVersionMajor(),
                    sshAvailability = resolveSshAvailability(),
                    error = null,
                )
            }
            try {
                repository.listClusterUpdates().fold(
                    onSuccess = { list ->
                        _ui.update { state ->
                            val updatedProgress = state.progress.toMutableMap()
                            list.forEach { snap ->
                                if (!updatedProgress.containsKey(snap.node)) {
                                    updatedProgress[snap.node] = NodeRefreshProgress(
                                        node = snap.node,
                                        state = NodeRefreshState.IDLE,
                                        detail = if (snap.updateCount == 0) "ALL PACKAGES UP TO DATE"
                                        else "${snap.updateCount} PACKAGES AVAILABLE",
                                    )
                                }
                            }
                            state.copy(
                                nodes = list,
                                loading = false,
                                refreshing = false,
                                progress = updatedProgress,
                                error = null,
                            )
                        }
                        // Fetch node telemetry for hybrid display
                        list.forEach { snap ->
                            launch {
                                repository.nodeStatus(snap.node).onSuccess { status ->
                                    _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (snap.node to status)) }
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        _ui.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                error = e.message ?: "Failed to load updates",
                            )
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "Failed to load updates",
                    )
                }
            }
        }
    }

    /** Refresh apt package list on a node with capped visual progression. */
    fun refreshAptDb(node: String) {
        val current = _ui.value.progress[node]
        if (current?.state == NodeRefreshState.PARSING || current?.state == NodeRefreshState.UPGRADING) {
            return
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val snap = _ui.value.nodes.find { it.node == node }
            val initialPackages = snap?.updates.orEmpty()
            val initialCount = initialPackages.size
            val initialSecCount = initialPackages.count { isSecurityUpdate(it) }

            _ui.update { state ->
                val prog = NodeRefreshProgress(
                    node = node,
                    state = NodeRefreshState.PARSING,
                    activePackageIndex = if (initialCount > 0) 0 else -1,
                    progressFraction = 0.08f,
                    detail = "CONTACTING $node · READING REPOSITORIES",
                    startTimeMs = startTime,
                )
                state.copy(progress = state.progress + (node to prog))
            }

            var backendSuccess = false
            var backendError: String? = null
            var is403Detected = false
            var taskUpid: String? = null

            val backendJob = launch {
                try {
                    repository.refreshAptUpdates(node).fold(
                        onSuccess = { upid ->
                            if (upid.startsWith("UPID:")) {
                                taskUpid = upid
                                repository.setActiveAptTask(node, upid, "aptupdate")
                                repository.awaitTask(node, upid, timeoutMs = 180_000).fold(
                                    onSuccess = { task ->
                                        backendSuccess = task.isOk
                                        if (!task.isOk) {
                                            val errStr = task.exitstatus ?: task.status ?: "Task failed"
                                            if (isHttp403Message(errStr)) {
                                                is403Detected = true
                                            }
                                            backendError = errStr
                                        }
                                    },
                                    onFailure = { e ->
                                        if (isHttp403(e)) {
                                            is403Detected = true
                                        }
                                        backendError = e.message ?: "Task timeout"
                                    },
                                )
                            } else {
                                backendSuccess = true
                            }
                        },
                        onFailure = { e ->
                            if (isHttp403(e)) {
                                is403Detected = true
                            }
                            backendError = e.message ?: "Could not start apt refresh"
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isHttp403(e)) {
                        is403Detected = true
                    }
                    backendError = e.message ?: "Apt refresh failed"
                } finally {
                    repository.clearActiveAptTask(taskUpid)
                }
            }

            // Cap the visual walk at AT MOST ~12 packages (~10s total)
            val maxWalk = minOf(initialCount, 12)
            if (maxWalk > 0) {
                for (i in 0 until maxWalk) {
                    if (backendJob.isCompleted) break
                    val pkg = initialPackages[i]
                    val frac = ((i + 1).toFloat() / (maxWalk + 1)).coerceIn(0.12f, 0.92f)
                    _ui.update { state ->
                        val existing = state.progress[node] ?: return@update state
                        state.copy(
                            progress = state.progress + (node to existing.copy(
                                activePackageIndex = i,
                                progressFraction = frac,
                                detail = "PARSING ${pkg.packageName?.uppercase(Locale.US) ?: "PACKAGE"}",
                            )),
                        )
                    }
                    delay(800L)
                }
                // If walk finished but backend is still running, hold the last package's state
                if (!backendJob.isCompleted) {
                    _ui.update { state ->
                        val existing = state.progress[node] ?: return@update state
                        state.copy(
                            progress = state.progress + (node to existing.copy(
                                activePackageIndex = maxWalk - 1,
                                progressFraction = 0.92f,
                                detail = "PARSING PACKAGE REPOSITORIES…",
                            )),
                        )
                    }
                }
            } else {
                delay(800L)
            }

            backendJob.join()

            val endTime = System.currentTimeMillis()
            val elapsedSec = (((endTime - startTime) / 100).toDouble() / 10.0).coerceAtLeast(0.8)

            if (backendSuccess) {
                try {
                    repository.listClusterUpdates().onSuccess { updatedNodes ->
                        _ui.update { state -> state.copy(nodes = updatedNodes) }
                    }
                    repository.nodeStatus(node).onSuccess { status ->
                        _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (node to status)) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}

                val finalSnap = _ui.value.nodes.find { it.node == node }
                val finalCount = finalSnap?.updateCount ?: initialCount
                val finalSec = finalSnap?.updates?.count { isSecurityUpdate(it) } ?: initialSecCount

                _ui.update { state ->
                    val existing = state.progress[node] ?: return@update state
                    val prog = existing.copy(
                        state = NodeRefreshState.COMPLETE,
                        activePackageIndex = -1,
                        progressFraction = 1.0f,
                        detail = "$finalCount PACKAGES · $finalSec SECURITY · TOOK ${"%.1f".format(Locale.US, elapsedSec)}S",
                        elapsedSec = elapsedSec,
                        completedPackagesCount = finalCount,
                        securityPackagesCount = finalSec,
                    )
                    state.copy(progress = state.progress + (node to prog))
                }
            } else {
                _ui.update { state ->
                    val existing = state.progress[node] ?: return@update state
                    val isPrivDenied = is403Detected || isHttp403Message(backendError)
                    val prog = existing.copy(
                        state = NodeRefreshState.ERROR,
                        activePackageIndex = -1,
                        progressFraction = 0f,
                        detail = "REFRESH FAILED",
                        errorDetail = if (isPrivDenied) PRIVILEGE_DENIED_COPY else (backendError ?: "Unknown error"),
                        isPrivilegeDenied = isPrivDenied,
                        elapsedSec = elapsedSec,
                    )
                    state.copy(progress = state.progress + (node to prog))
                }
            }
        }
    }

    /** Install/Upgrade apt packages on a PVE 9 node over SSH with live telemetry & streaming output. */
    fun installViaSsh(node: String) {
        val current = _ui.value.progress[node]
        if (current?.state == NodeRefreshState.PARSING || current?.state == NodeRefreshState.UPGRADING) {
            return
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val snap = _ui.value.nodes.find { it.node == node }
            val initialPackages = snap?.updates.orEmpty()
            val initialCount = initialPackages.size

            _ui.update { state ->
                val prog = NodeRefreshProgress(
                    node = node,
                    state = NodeRefreshState.UPGRADING,
                    activePackageIndex = if (initialCount > 0) 0 else -1,
                    progressFraction = 0.08f,
                    detail = "STARTING SSH UPGRADE ON $node…",
                    startTimeMs = startTime,
                    remoteUpgradeRemoved = true,
                )
                state.copy(progress = state.progress + (node to prog))
            }

            // Initial telemetry snapshot
            repository.nodeStatus(node).onSuccess { status ->
                _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (node to status)) }
            }

            // Live telemetry polling while upgrading
            val telemetryPollJob = launch {
                while (true) {
                    delay(1500L)
                    try {
                        repository.nodeStatus(node).onSuccess { status ->
                            _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (node to status)) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            }

            var lineCount = 0
            val sshResult = repository.sshUpgrade(node) { line ->
                lineCount++
                val fraction = (0.10f + (lineCount.toFloat() / 25f) * 0.80f).coerceIn(0.12f, 0.92f)
                _ui.update { state ->
                    val existing = state.progress[node] ?: return@update state
                    val newLog = (existing.logLines + line).takeLast(20)
                    state.copy(
                        progress = state.progress + (node to existing.copy(
                            progressFraction = fraction,
                            detail = line.take(80),
                            logLines = newLog,
                        )),
                    )
                }
            }

            telemetryPollJob.cancel()

            val endTime = System.currentTimeMillis()
            val elapsedSec = (((endTime - startTime) / 100).toDouble() / 10.0).coerceAtLeast(1.0)

            sshResult.fold(
                onSuccess = {
                    try {
                        repository.listClusterUpdates().onSuccess { updatedNodes ->
                            _ui.update { state -> state.copy(nodes = updatedNodes) }
                        }
                        repository.nodeStatus(node).onSuccess { status ->
                            _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (node to status)) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}

                    _ui.update { state ->
                        val existing = state.progress[node] ?: return@update state
                        val prog = existing.copy(
                            state = NodeRefreshState.COMPLETE,
                            activePackageIndex = -1,
                            progressFraction = 1.0f,
                            detail = "$initialCount PACKAGES UPGRADED VIA SSH · TOOK ${"%.1f".format(Locale.US, elapsedSec)}S",
                            elapsedSec = elapsedSec,
                            completedPackagesCount = initialCount,
                        )
                        state.copy(progress = state.progress + (node to prog))
                    }
                },
                onFailure = { e ->
                    _ui.update { state ->
                        val existing = state.progress[node] ?: return@update state
                        val prog = existing.copy(
                            state = NodeRefreshState.ERROR,
                            activePackageIndex = -1,
                            progressFraction = 0f,
                            detail = "SSH UPGRADE FAILED",
                            errorDetail = e.message ?: "SSH upgrade failed",
                            elapsedSec = elapsedSec,
                        )
                        state.copy(progress = state.progress + (node to prog))
                    }
                },
            )
        }
    }

    /** Install/Upgrade apt packages on a node with live telemetry & background fill progression. */
    fun installUpdates(node: String) {
        if (_ui.value.isRemoteUpgradeRemoved(node)) {
            if (_ui.value.isPasswordAuth) {
                installViaSsh(node)
            } else {
                _ui.update { state ->
                    val existing = state.progress[node] ?: NodeRefreshProgress(node = node)
                    state.copy(
                        remoteUpgradeRemovedNodes = state.remoteUpgradeRemovedNodes + node,
                        progress = state.progress + (node to existing.copy(
                            state = NodeRefreshState.IDLE,
                            remoteUpgradeRemoved = true,
                            detail = "REMOTE UPGRADE NOT AVAILABLE",
                        )),
                    )
                }
            }
            return
        }

        val current = _ui.value.progress[node]
        if (current?.state == NodeRefreshState.PARSING || current?.state == NodeRefreshState.UPGRADING) {
            return
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val snap = _ui.value.nodes.find { it.node == node }
            val initialPackages = snap?.updates.orEmpty()
            val initialCount = initialPackages.size

            _ui.update { state ->
                val prog = NodeRefreshProgress(
                    node = node,
                    state = NodeRefreshState.UPGRADING,
                    activePackageIndex = if (initialCount > 0) 0 else -1,
                    progressFraction = 0.08f,
                    detail = "STARTING DIST-UPGRADE ON $node…",
                    startTimeMs = startTime,
                )
                state.copy(progress = state.progress + (node to prog))
            }

            var backendSuccess = false
            var backendError: String? = null
            var is501Detected = false
            var is403Detected = false
            var taskUpid: String? = null

            // Initial telemetry snapshot
            repository.nodeStatus(node).onSuccess { status ->
                _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (node to status)) }
            }

            val backendJob = launch {
                try {
                    repository.aptUpgrade(node).fold(
                        onSuccess = { upid ->
                            if (upid.startsWith("UPID:")) {
                                taskUpid = upid
                                repository.setActiveAptTask(node, upid, "aptupgrade")
                                repository.awaitTask(node, upid, timeoutMs = 600_000).fold(
                                    onSuccess = { task ->
                                        backendSuccess = task.isOk
                                        if (!task.isOk) {
                                            val errStr = task.exitstatus ?: task.status ?: "Upgrade failed"
                                            if (isHttp403Message(errStr)) {
                                                is403Detected = true
                                            }
                                            backendError = errStr
                                        }
                                    },
                                    onFailure = { e ->
                                        if (isHttp501(e)) {
                                            is501Detected = true
                                        } else if (isHttp403(e)) {
                                            is403Detected = true
                                            backendError = e.message ?: "Upgrade permission denied"
                                        } else {
                                            backendError = e.message ?: "Upgrade timeout"
                                        }
                                    },
                                )
                            } else {
                                backendSuccess = true
                            }
                        },
                        onFailure = { e ->
                            if (isHttp501(e)) {
                                is501Detected = true
                            } else if (isHttp403(e)) {
                                is403Detected = true
                                backendError = e.message ?: "Could not start apt upgrade"
                            } else {
                                backendError = e.message ?: "Could not start apt upgrade"
                            }
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isHttp501(e)) {
                        is501Detected = true
                    } else if (isHttp403(e)) {
                        is403Detected = true
                        backendError = e.message ?: "Apt upgrade failed"
                    } else {
                        backendError = e.message ?: "Apt upgrade failed"
                    }
                } finally {
                    repository.clearActiveAptTask(taskUpid)
                }
            }

            // Live telemetry polling while upgrading
            val telemetryPollJob = launch {
                while (true) {
                    delay(1500L)
                    if (backendJob.isCompleted) break
                    try {
                        repository.nodeStatus(node).onSuccess { status ->
                            _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (node to status)) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                }
            }

            // Per-package walk with the 12-package cap
            val maxWalk = minOf(initialCount, 12)
            if (maxWalk > 0) {
                for (i in 0 until maxWalk) {
                    if (backendJob.isCompleted) break
                    val pkg = initialPackages[i]
                    val frac = ((i + 1).toFloat() / (maxWalk + 1)).coerceIn(0.12f, 0.92f)

                    // Fetch latest log line for detail if available
                    val taskLine = taskUpid?.let {
                        repository.taskLog(node, it, limit = 3).getOrNull()?.lastOrNull()
                    }
                    val detailMsg = taskLine ?: "UPGRADING ${pkg.packageName?.uppercase(Locale.US) ?: "PACKAGE"}"

                    _ui.update { state ->
                        val existing = state.progress[node] ?: return@update state
                        state.copy(
                            progress = state.progress + (node to existing.copy(
                                activePackageIndex = i,
                                progressFraction = frac,
                                detail = detailMsg,
                            )),
                        )
                    }
                    delay(800L)
                }
                if (!backendJob.isCompleted) {
                    _ui.update { state ->
                        val existing = state.progress[node] ?: return@update state
                        state.copy(
                            progress = state.progress + (node to existing.copy(
                                activePackageIndex = maxWalk - 1,
                                progressFraction = 0.92f,
                                detail = "INSTALLING UPGRADES ON $node…",
                            )),
                        )
                    }
                }
            } else {
                delay(800L)
            }

            backendJob.join()
            telemetryPollJob.cancel()

            if (is501Detected) {
                _ui.update { state ->
                    val existing = state.progress[node] ?: return@update state
                    state.copy(
                        remoteUpgradeRemovedNodes = state.remoteUpgradeRemovedNodes + node,
                        progress = state.progress + (node to existing.copy(
                            state = NodeRefreshState.IDLE,
                            remoteUpgradeRemoved = true,
                            activePackageIndex = -1,
                            progressFraction = 0f,
                            detail = "REMOTE UPGRADE NOT AVAILABLE",
                        )),
                    )
                }
                return@launch
            }

            val endTime = System.currentTimeMillis()
            val elapsedSec = (((endTime - startTime) / 100).toDouble() / 10.0).coerceAtLeast(1.0)

            if (backendSuccess) {
                try {
                    repository.listClusterUpdates().onSuccess { updatedNodes ->
                        _ui.update { state -> state.copy(nodes = updatedNodes) }
                    }
                    repository.nodeStatus(node).onSuccess { status ->
                        _ui.update { s -> s.copy(nodeStatuses = s.nodeStatuses + (node to status)) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}

                _ui.update { state ->
                    val existing = state.progress[node] ?: return@update state
                    val prog = existing.copy(
                        state = NodeRefreshState.COMPLETE,
                        activePackageIndex = -1,
                        progressFraction = 1.0f,
                        detail = "$initialCount PACKAGES UPGRADED · TOOK ${"%.1f".format(Locale.US, elapsedSec)}S",
                        elapsedSec = elapsedSec,
                        completedPackagesCount = initialCount,
                    )
                    state.copy(progress = state.progress + (node to prog))
                }
            } else {
                _ui.update { state ->
                    val existing = state.progress[node] ?: return@update state
                    val isPrivDenied = is403Detected || isHttp403Message(backendError)
                    val prog = existing.copy(
                        state = NodeRefreshState.ERROR,
                        activePackageIndex = -1,
                        progressFraction = 0f,
                        detail = "UPGRADE FAILED",
                        errorDetail = if (isPrivDenied) PRIVILEGE_DENIED_COPY else (backendError ?: "Unknown error"),
                        isPrivilegeDenied = isPrivDenied,
                        elapsedSec = elapsedSec,
                    )
                    state.copy(progress = state.progress + (node to prog))
                }
            }
        }
    }

    fun dismissProgress(node: String) {
        _ui.update { state ->
            val updatedProgress = state.progress.toMutableMap()
            val current = updatedProgress[node]
            if (current?.state == NodeRefreshState.COMPLETE || current?.state == NodeRefreshState.ERROR) {
                updatedProgress[node] = NodeRefreshProgress(
                    node = node,
                    state = NodeRefreshState.IDLE,
                    detail = if (state.nodes.find { it.node == node }?.updateCount == 0) "ALL PACKAGES UP TO DATE"
                    else "${state.nodes.find { it.node == node }?.updateCount} PACKAGES AVAILABLE",
                )
            }
            state.copy(progress = updatedProgress)
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val sessionStore: SessionStore = repository.sessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpdatesViewModel(repository, sessionStore) as T
        }
    }
}
