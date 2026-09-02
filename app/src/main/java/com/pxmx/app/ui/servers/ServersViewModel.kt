package com.pxmx.app.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerCardState(
    val profileId: String,
    val label: String,
    val host: String,
    val loading: Boolean = false,
    val online: Boolean = false,
    val version: String? = null,
    val running: Int = 0,
    val stopped: Int = 0,
    val guests: List<Pair<String, String>> = emptyList(),
    val errorText: String? = null,
    val hasSavedSecret: Boolean = true
)

data class ServersUiState(
    val servers: List<ServerCardState> = emptyList(),
    val refreshing: Boolean = false
)

class ServersViewModel(
    private val repository: ProxmoxRepository,
    private val sessionStore: SessionStore
) : ViewModel() {

    private val _ui = MutableStateFlow(ServersUiState())
    val ui: StateFlow<ServersUiState> = _ui.asStateFlow()

    init {
        probeAll()
    }

    fun refresh() {
        probeAll()
    }

    private fun probeAll() {
        // Publish mode: while the demo session is active, only the demo profile
        // is probed and shown. Real profiles stay stored and return for
        // non-demo sessions.
        val activeIsDemo = sessionStore.session.value?.config?.host
            ?.equals("demo", ignoreCase = true) == true
        val profiles = sessionStore.listProfiles().let { all ->
            if (activeIsDemo) all.filter { it.host.equals("demo", ignoreCase = true) } else all
        }
        if (profiles.isEmpty()) {
            _ui.update { ServersUiState() }
            return
        }

        _ui.update { state ->
            state.copy(
                refreshing = true,
                servers = profiles.map { p ->
                    ServerCardState(
                        profileId = p.id,
                        label = p.displayLabel,
                        host = p.displayHost,
                        loading = true,
                        hasSavedSecret = p.hasSavedSecret || p.host.lowercase() == "demo"
                    )
                }
            )
        }

        viewModelScope.launch {
            coroutineScope {
                profiles.map { profile ->
                    async {
                        val result = if (profile.hasSavedSecret || profile.host.lowercase() == "demo") {
                            repository.probeProfile(profile)
                        } else {
                            Result.failure(Exception("No saved credentials"))
                        }
                        
                        _ui.update { state ->
                            state.copy(
                                servers = state.servers.map { s ->
                                    if (s.profileId == profile.id) {
                                        result.fold(
                                            onSuccess = { probe ->
                                                s.copy(
                                                    loading = false,
                                                    online = true,
                                                    version = probe.version,
                                                    running = probe.running,
                                                    stopped = probe.stopped,
                                                    guests = probe.guests,
                                                    errorText = null
                                                )
                                            },
                                            onFailure = { e ->
                                                s.copy(
                                                    loading = false,
                                                    online = false,
                                                    errorText = if (e.message == "No saved credentials") null else e.message ?: "Offline"
                                                )
                                            }
                                        )
                                    } else s
                                }
                            )
                        }
                    }
                }
            }
            _ui.update { it.copy(refreshing = false) }
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val sessionStore: SessionStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ServersViewModel(repository, sessionStore) as T
        }
    }
}
