package com.pxmx.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo
import com.pxmx.app.data.repo.ProxmoxRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SdnUiState(
    val zones: List<SdnZoneInfo> = emptyList(),
    val vnets: List<SdnVnetInfo> = emptyList(),
    val statuses: List<SdnStatusInfo> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class SdnViewModel(
    private val repository: ProxmoxRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SdnUiState())
    val ui: StateFlow<SdnUiState> = _ui.asStateFlow()

    init {
        refresh(initial = true)
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
