package com.pxmx.app.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.ConsoleSession
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.repo.ProxmoxRepository
import com.pxmx.app.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConsoleUiState(
    val loading: Boolean = true,
    val session: ConsoleSession? = null,
    val trustSelfSigned: Boolean = false,
    val certPin: String? = null,
    val error: String? = null,
)

class ConsoleViewModel(
    private val repository: ProxmoxRepository,
    private val sessionStore: SessionStore,
    private val node: String,
    private val guestType: GuestType,
    private val vmid: Long,
    private val name: String,
    private val cmd: String? = null,
) : ViewModel() {

    private val host: String
        get() = sessionStore.session.value?.config?.host.orEmpty()

    private val _ui = MutableStateFlow(
        ConsoleUiState(
            trustSelfSigned = sessionStore.session.value?.config?.trustSelfSigned ?: false,
            certPin = sessionStore.getCertPin(sessionStore.session.value?.config?.host.orEmpty()),
        ),
    )
    val ui: StateFlow<ConsoleUiState> = _ui.asStateFlow()

    init {
        open()
    }

    fun open() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.openConsole(node, guestType, vmid, name, cmd).fold(
                onSuccess = { session ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            session = session,
                            trustSelfSigned = sessionStore.session.value?.config?.trustSelfSigned
                                ?: false,
                            certPin = sessionStore.getCertPin(host),
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(loading = false, error = e.message ?: "Console failed")
                    }
                },
            )
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val sessionStore: SessionStore,
        private val node: String,
        private val guestType: GuestType,
        private val vmid: Long,
        private val name: String,
        private val cmd: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConsoleViewModel(repository, sessionStore, node, guestType, vmid, name, cmd) as T
        }
    }
}
