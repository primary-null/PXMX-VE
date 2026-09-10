package com.pxmx.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.ClusterLogEntry
import com.pxmx.app.ui.adaptive.formatLogPriority
import com.pxmx.app.ui.adaptive.isOperatorTwoPane
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechPlateShape
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.logSeverityColor
import com.pxmx.app.ui.components.techTopAppBarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var selectedLogKey by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = techTopAppBarColors(),
                title = {
                    val scopeLabel = if (state.selectedScope == "cluster") "CLUSTER SYSLOG"
                    else "NODE ${state.selectedScope.uppercase()} SYSLOG"
                    Column {
                        Text("Logs")
                        Text(
                            if (state.logs.isEmpty()) scopeLabel
                            else "${state.logs.size} ENTRIES · $scopeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
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
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading && !state.refreshing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.logs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isTwoPane = isOperatorTwoPane(maxWidth.value.toInt())

            BackHandler(enabled = isTwoPane && selectedLogKey != null) {
                selectedLogKey = null
            }
            BackHandler(enabled = !isTwoPane || selectedLogKey == null) {
                onBack()
            }

            if (isTwoPane) {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight(),
                        ) {
                            item(key = "two-pane-scope-selector") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val isClusterSelected = state.selectedScope == "cluster"
                                    ScopeTab(
                                        label = "CLUSTER",
                                        selected = isClusterSelected,
                                        onClick = { viewModel.selectScope("cluster") },
                                    )
                                    state.nodeNames.forEach { nodeName ->
                                        val isNodeSelected = state.selectedScope == nodeName
                                        ScopeTab(
                                            label = "NODE: ${nodeName.uppercase()}",
                                            selected = isNodeSelected,
                                            onClick = { viewModel.selectScope(nodeName) },
                                        )
                                    }
                                }
                            }

                            state.error?.let { err ->
                                item(key = "two-pane-error-header") {
                                    TechPlate(railColor = MaterialTheme.colorScheme.error) {
                                        Text(
                                            err,
                                            modifier = Modifier.padding(14.dp),
                                            color = MaterialTheme.colorScheme.error,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }

                            if (state.logs.isEmpty() && state.error == null) {
                                item(key = "two-pane-empty-log") {
                                    TechPlate(railColor = TechColors.LinkGreen) {
                                        Text(
                                            "NO LOG ENTRIES FOUND",
                                            modifier = Modifier.padding(14.dp),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = TechColors.LinkGreen,
                                        )
                                    }
                                }
                            }

                            items(
                                items = state.logs,
                                key = { "two-pane-${it.stableKey}" },
                            ) { entry ->
                                LogEntryPlate(
                                    entry = entry,
                                    isSelected = entry.stableKey == selectedLogKey,
                                    onClick = { selectedLogKey = entry.stableKey },
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(TechColors.Edge),
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            val selectedEntry = state.logs.firstOrNull { it.stableKey == selectedLogKey }
                            if (selectedEntry != null) {
                                LogDetailPane(entry = selectedEntry)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(TechColors.Hull)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TechPlate(
                                        railColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = "SELECT AN EVENT",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "Select a log entry to view full event details.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "scope-selector") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val isClusterSelected = state.selectedScope == "cluster"
                                ScopeTab(
                                    label = "CLUSTER",
                                    selected = isClusterSelected,
                                    onClick = { viewModel.selectScope("cluster") },
                                )
                                state.nodeNames.forEach { nodeName ->
                                    val isNodeSelected = state.selectedScope == nodeName
                                    ScopeTab(
                                        label = "NODE: ${nodeName.uppercase()}",
                                        selected = isNodeSelected,
                                        onClick = { viewModel.selectScope(nodeName) },
                                    )
                                }
                            }
                        }
                        state.error?.let { err ->
                            item(key = "error-header") {
                                TechPlate(railColor = MaterialTheme.colorScheme.error) {
                                    Text(
                                        err,
                                        modifier = Modifier.padding(14.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }

                        if (state.logs.isEmpty() && state.error == null) {
                            item(key = "empty-log") {
                                TechPlate(railColor = TechColors.LinkGreen) {
                                    Text(
                                        "NO LOG ENTRIES FOUND",
                                        modifier = Modifier.padding(14.dp),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = TechColors.LinkGreen,
                                    )
                                }
                            }
                        }

                        items(
                            items = state.logs,
                            key = { it.stableKey },
                        ) { entry ->
                            LogEntryPlate(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryPlate(
    entry: ClusterLogEntry,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val severityColor = logSeverityColor(entry.pri)
    val railColor = if (isSelected) MaterialTheme.colorScheme.primary else severityColor
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else TechColors.Edge
    val shape = TechPlateShape
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) TechColors.Deck else TechColors.Hull)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(railColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(severityColor, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatLogTime(entry.time),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    entry.node?.let { node ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = node,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    entry.tag?.let { tag ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        val tagWithPid = if (entry.pid != null) "$tag[${entry.pid}]" else tag
                        Text(
                            text = tagWithPid,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    entry.user?.let { user ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = user,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.msg.orEmpty().ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (onClick != null) 2 else Int.MAX_VALUE,
                )
            }
        }
    }
}

@Composable
private fun LogDetailPane(
    entry: ClusterLogEntry,
    modifier: Modifier = Modifier,
) {
    val priColor = logSeverityColor(entry.pri)
    val priLabel = formatLogPriority(entry.pri)
    val timeLabel = formatLogDateTime(entry.time)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TechPlate(railColor = priColor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "EVENT DETAILS",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = priLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = priColor,
                        modifier = Modifier
                            .border(1.dp, priColor.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                            .background(priColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                LogDetailRow(label = "TIME", value = timeLabel)
                entry.node?.let { node ->
                    Spacer(Modifier.height(8.dp))
                    LogDetailRow(label = "NODE", value = node)
                }
                entry.tag?.let { tag ->
                    Spacer(Modifier.height(8.dp))
                    val tagWithPid = if (entry.pid != null) "$tag[${entry.pid}]" else tag
                    LogDetailRow(label = "TAG", value = tagWithPid)
                }
                entry.user?.let { user ->
                    Spacer(Modifier.height(8.dp))
                    LogDetailRow(label = "USER", value = user)
                }
                entry.pri?.let { priVal ->
                    Spacer(Modifier.height(8.dp))
                    LogDetailRow(label = "SEVERITY", value = "$priVal ($priLabel)")
                }
            }
        }

        TechSectionLabel(title = "MESSAGE", accent = TechColors.Edge)
        TechPlate(railColor = TechColors.Edge) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TechColors.Deck)
                    .padding(16.dp),
            ) {
                Text(
                    text = entry.msg.orEmpty().ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LogDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatLogDateTime(epochSec: Long?): String {
    if (epochSec == null || epochSec <= 0) return "—"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return sdf.format(Date(epochSec * 1000L))
}

private fun formatLogTime(epochSec: Long?): String {
    if (epochSec == null || epochSec <= 0) return "—"
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
    return sdf.format(Date(epochSec * 1000L))
}

@Composable
private fun ScopeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(bottomEnd = 8.dp)
    val border = if (selected) MaterialTheme.colorScheme.primary else TechColors.Edge
    val bg = if (selected) TechColors.Deck else TechColors.Hull
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, border, shape)
            .background(bg, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
