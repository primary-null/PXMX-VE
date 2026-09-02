package com.pxmx.app.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.data.model.StorageStatus
import com.pxmx.app.data.repo.ProxmoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageDetailUiState(
    val node: String,
    val storage: String,
    val status: StorageStatus? = null,
    val content: List<StorageContentItem> = emptyList(),
    /** null = all content types */
    val contentFilter: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val confirmDelete: StorageContentItem? = null,
) {
    val availableTypes: List<String>
        get() = content.mapNotNull { it.content }.distinct().sorted()

    val filtered: List<StorageContentItem>
        get() = if (contentFilter == null) content
        else content.filter { it.content == contentFilter }
}

class StorageDetailViewModel(
    private val repository: ProxmoxRepository,
    node: String,
    storage: String,
) : ViewModel() {

    private val _ui = MutableStateFlow(StorageDetailUiState(node = node, storage = storage))
    val ui: StateFlow<StorageDetailUiState> = _ui.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun setFilter(type: String?) {
        _ui.update { it.copy(contentFilter = type) }
    }

    fun refresh(initial: Boolean = false) {
        val s = _ui.value
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = initial,
                    refreshing = !initial,
                    error = null,
                )
            }
            // Load all content; filter client-side for chips
            repository.storageDetail(s.node, s.storage, contentFilter = null).fold(
                onSuccess = { detail ->
                    _ui.update {
                        it.copy(
                            status = detail.status,
                            content = detail.content,
                            loading = false,
                            refreshing = false,
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = e.message ?: "Failed to load storage",
                        )
                    }
                },
            )
        }
    }

    fun confirmDelete(item: StorageContentItem?) {
        _ui.update { it.copy(confirmDelete = item) }
    }

    fun deleteConfirmed() {
        val item = _ui.value.confirmDelete ?: return
        val volid = item.volid ?: return
        val node = _ui.value.node
        _ui.update { it.copy(confirmDelete = null, busy = true, message = null, error = null) }
        viewModelScope.launch {
            repository.deleteStorageVolume(node, volid).fold(
                onSuccess = {
                    _ui.update { it.copy(busy = false, message = "Deleted $volid") }
                    refresh()
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(busy = false, error = e.message ?: "Delete failed")
                    }
                },
            )
        }
    }

    class Factory(
        private val repository: ProxmoxRepository,
        private val node: String,
        private val storage: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StorageDetailViewModel(repository, node, storage) as T
        }
    }
}
