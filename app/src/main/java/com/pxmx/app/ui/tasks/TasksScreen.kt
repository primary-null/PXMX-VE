package com.pxmx.app.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import com.pxmx.app.ui.icons.InstrumentGlyphs
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ui.adaptive.isOperatorTwoPane
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    BackHandler(enabled = state.selectedTask != null) {
        viewModel.clearSelectedTask()
    }
    BackHandler(enabled = state.selectedTask == null) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks")
                        Text(
                            " CLUSTER TASKS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.selectedTask != null) {
                            viewModel.clearSelectedTask()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading && !state.refreshing,
                    ) {
                        Icon(InstrumentGlyphs.Refresh, contentDescription = "Refresh", tint = Color.Unspecified)
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isTwoPane = isOperatorTwoPane(maxWidth.value.toInt())

            if (isTwoPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight(),
                    ) {
                        TasksListPane(
                            state = state,
                            onRefresh = { viewModel.refresh() },
                            onSelectTask = { viewModel.selectTask(it) },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(TechColors.Edge.copy(alpha = 0.4f)),
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        val activeTask = state.selectedTask
                        if (activeTask != null) {
                            TaskDetailPane(
                                task = activeTask,
                                logLines = state.taskLogLines,
                                isLoadingLogs = state.taskLogLoading,
                                logError = state.taskLogError,
                                onRefreshLogs = { viewModel.refreshTaskLog() },
                                onDismiss = { viewModel.clearSelectedTask() },
                            )
                        } else {
                            TaskEmptySelectionPane()
                        }
                    }
                }
            } else {
                TasksListPane(
                    state = state,
                    onRefresh = { viewModel.refresh() },
                    onSelectTask = { viewModel.selectTask(it) },
                )

                if (state.selectedTask != null) {
                    val activeTask = state.selectedTask!!
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.clearSelectedTask() },
                        containerColor = TechColors.Hull,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        scrimColor = Color.Black.copy(alpha = 0.65f),
                        dragHandle = {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(TechColors.Edge, RoundedCornerShape(2.dp)),
                            )
                        },
                    ) {
                        TaskDetailPane(
                            task = activeTask,
                            logLines = state.taskLogLines,
                            isLoadingLogs = state.taskLogLoading,
                            logError = state.taskLogError,
                            onRefreshLogs = { viewModel.refreshTaskLog() },
                            onDismiss = { viewModel.clearSelectedTask() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksListPane(
    state: TasksUiState,
    onRefresh: () -> Unit,
    onSelectTask: (ClusterTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            state.error?.let { err ->
                item {
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

            if (state.tasks.isEmpty() && state.error == null) {
                item {
                    TechPlate(railColor = TechColors.LinkGreen) {
                        Text(
                            "NO TASKS FOUND",
                            modifier = Modifier.padding(14.dp),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.LinkGreen,
                        )
                    }
                }
            }

            items(state.tasks, key = { it.upid }) { task ->
                TaskCard(
                    task = task,
                    isSelected = task.upid == state.selectedTask?.upid,
                    onClick = { onSelectTask(task) },
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun taskStatusChipBadge(
    chip: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CutCornerShape(bottomEnd = 4.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = modifier.widthIn(max = 96.dp),
    ) {
        Text(
            text = chip,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun taskStatusColor(chip: String): Color = when (chip) {
    "OK" -> TechColors.LinkGreen
    "RUNNING" -> TechColors.Amber
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun TaskCard(
    task: ClusterTask,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val chip = task.chip
    val detail = task.detail
    val railColor = taskStatusColor(chip)
    val locale = LocalLocale.current.platformLocale
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", locale)
    val selectedBorder = Modifier.border(1.dp, TechColors.LinkGreen.copy(alpha = 0.8f), RoundedCornerShape(2.dp))

    TechPlate(
        railColor = if (isSelected) TechColors.LinkGreen else railColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(if (isSelected) selectedBorder else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.type.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) TechColors.LinkGreen else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                taskStatusChipBadge(chip = chip, color = railColor)
            }
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            TechMetaLine("NODE", task.node)
            TechMetaLine("USER", task.user)
            TechMetaLine("START", fmt.format(Date(task.startTime * 1000)))
            task.endTime?.let { TechMetaLine("END", fmt.format(Date(it * 1000))) }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun TaskDetailPane(
    task: ClusterTask,
    logLines: List<String>,
    isLoadingLogs: Boolean,
    logError: String?,
    onRefreshLogs: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val chip = task.chip
    val detail = task.detail
    val isRunning = chip == "RUNNING"
    val statusColor = taskStatusColor(chip)

    val locale = LocalLocale.current.platformLocale
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
    val durationText = remember(task.startTime, task.endTime) {
        val end = task.endTime ?: (System.currentTimeMillis() / 1000L)
        val diffSec = (end - task.startTime).coerceAtLeast(0L)
        val mins = diffSec / 60
        val secs = diffSec % 60
        if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Task Header & Status ---
        TechPlate(railColor = statusColor) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TASK DETAILS",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = task.type.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.LinkGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        taskStatusChipBadge(chip = chip, color = statusColor)
                        IconButton(
                            onClick = onRefreshLogs,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Log",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (onDismiss != null) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (detail != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }


                Spacer(Modifier.height(14.dp))

                TechMetaLine("NODE", task.node)
                TechMetaLine("USER", task.user)
                TechMetaLine("START", fmt.format(Date(task.startTime * 1000)))
                task.endTime?.let {
                    TechMetaLine("END", fmt.format(Date(it * 1000)))
                }
                TechMetaLine(
                    "DURATION",
                    if (isRunning) "$durationText (RUNNING)" else durationText,
                )
                task.pid?.let {
                    TechMetaLine("PID", it.toString())
                }
            }
        }

        // --- UPID Plate with One-Touch Copy ---
        TechPlate(railColor = TechColors.CoolBlue) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "TASK UPID",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TechColors.CoolBlue,
                        letterSpacing = 0.8.sp,
                    )
                    Row(
                        modifier = Modifier
                            .clickable {
                                clipboardManager.setText(AnnotatedString(task.upid))
                                copied = true
                                scope.launch {
                                    delay(1800)
                                    copied = false
                                }
                            }
                            .border(1.dp, TechColors.CoolBlue.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .background(TechColors.CoolBlue.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy UPID",
                            tint = if (copied) TechColors.LinkGreen else TechColors.CoolBlue,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (copied) "COPIED" else "COPY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (copied) TechColors.LinkGreen else TechColors.CoolBlue,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = task.upid,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        .padding(8.dp),
                )
            }
        }

        // --- Monospace Task Log Terminal ---
        TechPlate(railColor = TechColors.Edge) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "TASK LOG OUTPUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                    if (isLoadingLogs) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = TechColors.LinkGreen,
                        )
                    } else {
                        Text(
                            text = "${logLines.size} LINES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                        .border(1.dp, TechColors.Edge.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(10.dp),
                ) {
                    when {
                        isLoadingLogs && logLines.isEmpty() -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = TechColors.LinkGreen,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "FETCHING TASK LOG...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        logError != null && logLines.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = logError,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "TAP REFRESH TO RETRY",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TechColors.CoolBlue,
                                    modifier = Modifier.clickable { onRefreshLogs() },
                                )
                            }
                        }
                        logLines.isEmpty() -> {
                            Text(
                                text = "NO LOG ENTRIES RECORDED FOR THIS TASK",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                logLines.forEach { line ->
                                    val isTaskOk = line.contains("TASK OK")
                                    val isTaskErr = line.contains("TASK ERROR") || line.contains("error", ignoreCase = true)
                                    val lineColor = when {
                                        isTaskOk -> TechColors.LinkGreen
                                        isTaskErr -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = lineColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskEmptySelectionPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        TechPlate(railColor = TechColors.Edge) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "TASK INSPECTOR",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SELECT A TASK TO INSPECT UPID TELEMETRY AND REAL-TIME LOG OUTPUT",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
