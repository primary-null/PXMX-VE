package com.pxmx.app.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.StorageContentItem
import com.pxmx.app.ui.components.MetricBar
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechIconBay
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.TechStatusPlate
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatEpoch
import com.pxmx.app.ui.util.formatPercent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDetailScreen(
    viewModel: StorageDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val st = state.status
    val used = st?.used
    val total = st?.total
    val pct = if (used != null && total != null && total > 0) {
        used.toDouble() / total.toDouble()
    } else null

    // Back handling: dismiss delete confirmation modal first if open, else navigate back
    BackHandler(enabled = state.confirmDelete != null) {
        viewModel.confirmDelete(null)
    }
    BackHandler(enabled = state.confirmDelete == null) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.storage)
                        Text(
                            "${state.node} · ${st?.type ?: "storage"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && st == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    TechPlate(
                        railColor = if (st?.active == 1) TechColors.Amber else TechColors.Mute,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                        ) {
                            TechIconBay(
                                icon = Icons.Default.Storage,
                                accent = MaterialTheme.colorScheme.primary,
                                size = 48.dp,
                                iconSize = 28.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.storage.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                )
                                Text(
                                    buildString {
                                        st?.type?.let { append(it.uppercase()) }
                                        if (st?.shared == 1) append(" · SHARED")
                                    }.ifBlank { "STORAGE" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TechStatusPlate(
                                status = if (st?.active == 1) "available" else "offline",
                            )
                        }
                        TechDeck(showAccentBar = true) {
                            MetricBar(
                                label = "Capacity",
                                valueText = "${formatBytes(used)} / ${formatBytes(total)} · ${formatPercent(pct?.times(100))}",
                                progress = pct?.toFloat(),
                            )
                            Spacer(Modifier.height(4.dp))
                            TechMetaLine("Avail", formatBytes(st?.avail))
                            st?.content?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                TechMetaLine("Allow", it.replace(",", " · "))
                            }
                        }
                    }
                }

                state.error?.let {
                    item { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                state.message?.let {
                    item { Text(it, color = MaterialTheme.colorScheme.primary) }
                }

                item {
                    TechSectionLabel("Content", count = state.content.size)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.contentFilter == null,
                                onClick = { viewModel.setFilter(null) },
                                label = { Text("All (${state.content.size})") },
                            )
                        }
                        // Prefer status-declared types, fall back to observed
                        val types = (st?.content?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                            ?: emptyList())
                            .ifEmpty { state.availableTypes }
                            .distinct()
                        items(types) { type ->
                            val count = state.content.count { it.content == type }
                            FilterChip(
                                selected = state.contentFilter == type,
                                onClick = { viewModel.setFilter(type) },
                                label = { Text("$type ($count)") },
                            )
                        }
                    }
                }

                val rows = state.filtered
                if (rows.isEmpty()) {
                    item {
                        Text(
                            "No content in this view",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(rows, key = { it.volid ?: it.hashCode().toString() }) { item ->
                        ContentCard(
                            item = item,
                            busy = state.busy,
                            onDelete = { viewModel.confirmDelete(item) },
                        )
                    }
                }
            }
        }
    }

    state.confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmDelete(null) },
            title = { Text("Delete volume?") },
            text = { Text("Delete ${item.volid}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteConfirmed() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmDelete(null) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ContentCard(
    item: StorageContentItem,
    busy: Boolean,
    onDelete: () -> Unit,
) {
    TechPlate(
        railColor = TechColors.Edge,
        showRail = true,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (item.volid?.substringAfter(':') ?: item.volid ?: "volume").uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    item.volid ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append((item.content ?: "?").uppercase())
                        item.format?.let { append(" · ").append(it.uppercase()) }
                        item.vmid?.let { append(" · VM ").append(it) }
                        append(" · ").append(formatBytes(item.size))
                        item.used?.let { append(" USED ").append(formatBytes(it)) }
                        item.ctime?.let { append(" · ").append(formatEpoch(it)) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
