package com.pxmx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.NodeNetworkSnapshot
import com.pxmx.app.data.repo.ProxmoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkUiState(
    val nodes: List<NodeNetworkSnapshot> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class NetworkViewModel(
    private val repository: ProxmoxRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(NetworkUiState())
    val ui: StateFlow<NetworkUiState> = _ui.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = initial && it.nodes.isEmpty(),
                    refreshing = !initial,
                    error = null,
                )
            }
            val net = repository.listClusterNetwork()
            _ui.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    nodes = net.getOrDefault(emptyList()),
                    error = net.exceptionOrNull()?.message,
                )
            }
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NetworkViewModel(repository) as T
        }
    }
}
