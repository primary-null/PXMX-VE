package com.pxmx.app.ui.node

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.NodeServiceInfo
import com.pxmx.app.data.model.NodeTaskInfo
import com.pxmx.app.ui.components.MetricBar
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.TechStatusPlate
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatEpoch
import com.pxmx.app.ui.util.formatPercent
import com.pxmx.app.ui.util.formatUptime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailScreen(
    viewModel: NodeDetailViewModel,
    onBack: () -> Unit,
    onOpenConsole: (String?) -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val st = state.status
    val mem = st?.memory
    val memPct = if (mem?.used != null && mem.total != null && mem.total > 0) {
        mem.used.toDouble() / mem.total.toDouble()
    } else null
    val root = st?.rootfs
    val diskPct = if (root?.used != null && root.total != null && root.total > 0) {
        root.used.toDouble() / root.total.toDouble()
    } else null

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.node.uppercase())
                        Text(
                            "NODE · STATUS · SERVICES · TASKS",
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
                    val context = LocalContext.current
                    IconButton(onClick = { onOpenConsole("login") }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Node shell")
                    }
                    IconButton(onClick = {
                        viewModel.getBrowserUrl()?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(Icons.Default.Language, contentDescription = "Open in browser")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && st == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let {
                    item { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                item {
                    TechPlate(railColor = TechColors.LinkGreen) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Text(
                                state.node.uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            TechMetaLine("Uptime", formatUptime(st?.uptime))
                            st?.pveversion?.let { TechMetaLine("PVE", it) }
                            st?.kversion?.let { TechMetaLine("Kernel", it) }
                            st?.loadavg?.let { loads ->
                                TechMetaLine("Load", loads.joinToString("  "))
                            }
                        }
                        TechDeck {
                            MetricBar(
                                label = "CPU",
                                valueText = formatPercent(st?.cpu?.times(100)),
                                progress = st?.cpu?.toFloat()?.coerceIn(0f, 1f),
                            )
                            MetricBar(
                                label = "Memory",
                                valueText = "${formatBytes(mem?.used)} / ${formatBytes(mem?.total)}",
                                progress = memPct?.toFloat(),
                            )
                            MetricBar(
                                label = "Rootfs",
                                valueText = "${formatBytes(root?.used)} / ${formatBytes(root?.total)}",
                                progress = diskPct?.toFloat(),
                            )
                        }
                    }
                }

                item {
                    TechPlate(railColor = TechColors.Amber) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                            Text(
                                "CONSOLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onOpenConsole("login") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(2.dp),
                                ) {
                                    Text("SHELL", fontFamily = FontFamily.Monospace)
                                }
                                OutlinedButton(
                                    onClick = { onOpenConsole("upgrade") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(2.dp),
                                ) {
                                    Text("UPGRADE", fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text(
                                "XTERM.JS · NODE ACCESS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.6.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                item {
                    TechSectionLabel(
                        "Services",
                        count = state.services.size,
                        accent = TechColors.Amber,
                    )
                }
                if (state.services.isEmpty()) {
                    item {
                        Text(
                            "No services reported",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.services, key = { it.name ?: it.hashCode().toString() }) { svc ->
                        ServiceRow(svc)
                    }
                }

                item {
                    TechSectionLabel(
                        "Recent tasks",
                        count = state.tasks.size,
                        accent = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.tasks.isEmpty()) {
                    item {
                        Text(
                            "No recent tasks",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.tasks, key = { it.upid ?: it.hashCode().toString() }) { task ->
                        TaskRow(task)
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun ServiceRow(svc: NodeServiceInfo) {
    TechPlate(railColor = if (svc.isActive) TechColors.LinkGreen else TechColors.Mute) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (svc.name ?: "?").uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                svc.desc?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TechStatusPlate(
                status = if (svc.isActive) "running" else (svc.state ?: "stopped"),
            )
        }
    }
}

@Composable
private fun TaskRow(task: NodeTaskInfo) {
    TechPlate(railColor = if (task.isRunning) TechColors.Amber else TechColors.Edge) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (task.type ?: "task").uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TechStatusPlate(status = task.status)
            }
            task.id?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "ID $it",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val whenTxt = task.starttime?.let { formatEpoch(it) } ?: "—"
            Text(
                listOfNotNull(task.user, whenTxt).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
