package com.pxmx.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.AptPackageUpdate
import com.pxmx.app.data.model.AptPackageVersion
import com.pxmx.app.data.model.NodeStatus
import com.pxmx.app.data.model.NodeUpdateSnapshot
import com.pxmx.app.ui.components.MetricBar
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechPlateShape
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.guest.detail.ConfirmDialog
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatPercent
import java.util.Locale

@Composable
private fun ResourceFillBackground(
    animatedUsage: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .drawWithContent {
                val fillWidth = size.width * animatedUsage
                drawRect(
                    color = fillColor.copy(alpha = 0.10f),
                    size = size.copy(width = fillWidth),
                )
                drawLine(
                    color = fillColor.copy(alpha = 0.4f),
                    start = Offset(fillWidth, 0f),
                    end = Offset(fillWidth, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawContent()
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    viewModel: UpdatesViewModel,
    onBack: () -> Unit,
    onOpenNode: (String) -> Unit,
    onOpenNodeShell: (String) -> Unit = {},
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var nodeToUpgrade by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var nodeToUpgradeSsh by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // Back handling: close upgrade confirmation dialogs first if open, else navigate back
    BackHandler(enabled = nodeToUpgrade != null) {
        nodeToUpgrade = null
    }
    BackHandler(enabled = nodeToUpgradeSsh != null) {
        nodeToUpgradeSsh = null
    }
    BackHandler(enabled = nodeToUpgrade == null && nodeToUpgradeSsh == null) {
        onBack()
    }

    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose {
            viewModel.setScreenActive(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Updates")
                        Text(
                            if (state.totalPending == 0) "ALL NODES CURRENT"
                            else "${state.totalPending} PKG · ${state.nodesWithUpdates} NODE(S)",
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
                        enabled = !state.loading && !state.anyJobActive,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.nodes.isEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let { err ->
                    item {
                        TechPlate(railColor = MaterialTheme.colorScheme.error) {
                            Text(
                                text = err.uppercase(Locale.US),
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                // If cluster has 0 pending packages and no jobs running
                if (state.totalPending == 0 && !state.anyJobActive && state.nodes.isNotEmpty()) {
                    item(key = "zero-state") {
                        TechPlate(railColor = TechColors.Edge) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "ALL NODES CURRENT",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TechColors.LinkGreen,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "No package updates pending across cluster",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Per-node sections
                state.nodes.forEach { snap ->
                    val progress = state.progress[snap.node]
                    val nodeStatus = state.nodeStatuses[snap.node]
                    val isUpgrading = progress?.state == NodeRefreshState.UPGRADING

                    item(key = "node-rect-${snap.node}") {
                        NodeStatusRectangle(
                            snap = snap,
                            progress = progress,
                            nodeStatus = nodeStatus,
                            serverHost = state.serverHost,
                            pveVersion = state.pveVersion,
                            isRemoteUpgradeRemoved = state.isRemoteUpgradeRemoved(snap.node),
                            sshAvailability = state.sshAvailability,
                            anyJobActive = state.anyJobActive,
                            onRefreshApt = { viewModel.refreshAptDb(snap.node) },
                            onInstallUpdates = {
                                nodeToUpgrade = snap.node to snap.updateCount
                            },
                            onInstallViaSsh = {
                                nodeToUpgradeSsh = snap.node to snap.updateCount
                            },
                            onOpenNodeShell = { onOpenNodeShell(snap.node) },
                            onClick = { onOpenNode(snap.node) },
                            onDismiss = { viewModel.dismissProgress(snap.node) },
                        )
                    }

                    if (snap.updates.isNotEmpty()) {
                        item(key = "pend-header-${snap.node}") {
                            TechSectionLabel(
                                title = "PENDING (${snap.updateCount})",
                                accent = if (isUpgrading) TechColors.Mute else TechColors.Amber,
                            )
                        }

                        itemsIndexed(
                            items = snap.updates,
                            key = { index, upd -> "${snap.node}-u-${upd.packageName}-$index" },
                        ) { index, upd ->
                            val isActive = (progress?.state == NodeRefreshState.PARSING || progress?.state == NodeRefreshState.UPGRADING) &&
                                progress.activePackageIndex == index
                            
                            // Task 1: During UPGRADING, completed rows drop away.
                            if (isUpgrading && progress != null && index < progress.activePackageIndex) {
                                return@itemsIndexed
                            }

                            PackageUpdateRow(
                                upd = upd,
                                node = snap.node,
                                isActive = isActive,
                                isUpgrading = isUpgrading,
                            )
                        }
                    }

                    val keyPkgs = snap.versions.filter { v ->
                        val n = v.packageName.orEmpty().lowercase(Locale.US)
                        n.contains("pve") || n.contains("proxmox") || n.contains("qemu") ||
                            n.contains("lxc") || n.contains("kernel")
                    }.ifEmpty { snap.versions.take(6) }

                    if (keyPkgs.isNotEmpty()) {
                        item(key = "stack-header-${snap.node}") {
                            TechSectionLabel(
                                title = "INSTALLED STACK",
                                accent = TechColors.Edge,
                            )
                        }
                        items(keyPkgs, key = { "${snap.node}-v-${it.packageName}" }) { ver ->
                            VersionCard(ver)
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    nodeToUpgrade?.let { (node, count) ->
        ConfirmDialog(
            title = "Install updates",
            body = "$count package(s) pending · runs apt-get dist-upgrade on $node · VMs and containers keep running · may take several minutes",
            confirm = "INSTALL",
            onDismiss = { nodeToUpgrade = null },
            onConfirm = {
                nodeToUpgrade = null
                viewModel.installUpdates(node)
            },
        )
    }

    nodeToUpgradeSsh?.let { (node, count) ->
        ConfirmDialog(
            title = "Install updates via SSH",
            body = "$count package(s) pending · runs apt-get full-upgrade on $node · VMs keep running · may take minutes",
            confirm = "INSTALL VIA SSH",
            onDismiss = { nodeToUpgradeSsh = null },
            onConfirm = {
                nodeToUpgradeSsh = null
                viewModel.installViaSsh(node)
            },
        )
    }
}

/** ONE big status rectangle per node being refreshed or upgraded. */
@Composable
private fun NodeStatusRectangle(
    snap: NodeUpdateSnapshot,
    progress: NodeRefreshProgress?,
    nodeStatus: NodeStatus?,
    serverHost: String,
    pveVersion: String,
    isRemoteUpgradeRemoved: Boolean = false,
    sshAvailability: SshUpgradeAvailability = SshUpgradeAvailability.AVAILABLE,
    anyJobActive: Boolean,
    onRefreshApt: () -> Unit,
    onInstallUpdates: () -> Unit,
    onInstallViaSsh: () -> Unit = {},
    onOpenNodeShell: () -> Unit = {},
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = progress?.state ?: NodeRefreshState.IDLE
    val isBusy = state == NodeRefreshState.PARSING || state == NodeRefreshState.UPGRADING

    val railColor = when (state) {
        NodeRefreshState.IDLE -> TechColors.Edge
        NodeRefreshState.PARSING, NodeRefreshState.UPGRADING -> TechColors.LinkGreen.copy(alpha = 0.55f)
        NodeRefreshState.COMPLETE -> TechColors.LinkGreen
        NodeRefreshState.ERROR -> MaterialTheme.colorScheme.error
    }
    
    // ... rest of the setup
    
    val tagText = when (state) {
        NodeRefreshState.IDLE -> "IDLE"
        NodeRefreshState.PARSING -> "PARSING"
        NodeRefreshState.UPGRADING -> "UPGRADING"
        NodeRefreshState.COMPLETE -> "COMPLETE"
        NodeRefreshState.ERROR -> "FAILED"
    }

    val tagColor = when (state) {
        NodeRefreshState.IDLE -> TechColors.Mute
        NodeRefreshState.PARSING, NodeRefreshState.UPGRADING -> TechColors.Amber
        NodeRefreshState.COMPLETE -> TechColors.LinkGreen
        NodeRefreshState.ERROR -> MaterialTheme.colorScheme.error
    }

    val stateText = when (state) {
        NodeRefreshState.IDLE -> "IDLE"
        NodeRefreshState.PARSING -> "PARSING PACKAGE LISTS"
        NodeRefreshState.UPGRADING -> "UPGRADING PACKAGES"
        NodeRefreshState.COMPLETE -> if (progress?.detail?.contains("UPGRADED") == true) "UPGRADE COMPLETE" else "UPDATE COMPLETE"
        NodeRefreshState.ERROR -> if (progress?.detail?.contains("UPGRADE") == true) "UPGRADE FAILED" else "REFRESH FAILED"
    }

    val stateTextColor = when (state) {
        NodeRefreshState.IDLE -> MaterialTheme.colorScheme.onSurface
        NodeRefreshState.PARSING, NodeRefreshState.UPGRADING -> TechColors.Amber
        NodeRefreshState.COMPLETE -> TechColors.LinkGreen
        NodeRefreshState.ERROR -> MaterialTheme.colorScheme.error
    }

    val detailText = when (state) {
        NodeRefreshState.IDLE -> if (snap.updateCount == 0) "ALL PACKAGES UP TO DATE"
        else "${snap.updateCount} PACKAGES AVAILABLE"
        NodeRefreshState.PARSING -> progress?.detail ?: "CONTACTING ${snap.node.uppercase(Locale.US)} · READING REPOSITORIES"
        NodeRefreshState.UPGRADING -> progress?.detail ?: "RUNNING APT-GET DIST-UPGRADE ON ${snap.node.uppercase(Locale.US)}"
        NodeRefreshState.COMPLETE -> progress?.detail ?: "${snap.updateCount} PACKAGES AVAILABLE"
        NodeRefreshState.ERROR -> progress?.errorDetail ?: "Task failed or timed out"
    }

    val fillFraction by animateFloatAsState(
        targetValue = if (isBusy || state == NodeRefreshState.COMPLETE) {
            progress?.progressFraction ?: 0f
        } else 0f,
        animationSpec = tween(800),
        label = "bigRectFill",
    )

    val fillColor by animateColorAsState(
        targetValue = when {
            fillFraction < 0.60f -> TechColors.LinkGreen
            fillFraction < 0.85f -> TechColors.Amber
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(800),
        label = "bigRectColor",
    )

    val shape = TechPlateShape

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TechColors.Hull)
            .border(1.dp, TechColors.Edge, shape)
            .then(if (state == NodeRefreshState.COMPLETE || state == NodeRefreshState.ERROR) Modifier.clickable { onDismiss() } else Modifier.clickable { onClick() }),
    ) {
        // Task 1: During UPGRADING, slow background fill shifting through threshold colors
        if (state == NodeRefreshState.UPGRADING && fillFraction > 0f) {
            ResourceFillBackground(
                animatedUsage = fillFraction,
                fillColor = fillColor,
                modifier = Modifier.matchParentSize(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // 5dp status rail
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(railColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
            ) {
                // Top line: Node host name ~22sp bold + Status Tag (top-right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = snap.node.uppercase(Locale.US),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    StatusTag(text = tagText, color = tagColor)
                }

                // Second line: "host:port · PVE version" (small, muted)
                Text(
                    text = "$serverHost · $pveVersion",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(14.dp))

                // Big ~17sp state line
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = stateTextColor,
                )

                Spacer(Modifier.height(4.dp))

                // Detail line
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Task 1: During UPGRADING, hybrid TechDeck with live telemetry MetricBars
                if (state == NodeRefreshState.UPGRADING) {
                    val st = nodeStatus
                    val mem = st?.memory
                    val memPct = if (mem?.total != null && mem.total > 0 && mem.used != null) {
                        mem.used.toDouble() / mem.total.toDouble()
                    } else null
                    val root = st?.rootfs
                    val diskPct = if (root?.total != null && root.total > 0 && root.used != null) {
                        root.used.toDouble() / root.total.toDouble()
                    } else null

                    Spacer(Modifier.height(12.dp))
                    TechDeck {
                        MetricBar(
                            label = "CPU",
                            valueText = formatPercent(st?.cpu?.times(100)),
                            progress = st?.cpu?.toFloat()?.coerceIn(0f, 1f),
                            fillColor = MaterialTheme.colorScheme.primary,
                        )
                        MetricBar(
                            label = "Memory",
                            valueText = "${formatBytes(mem?.used)} / ${formatBytes(mem?.total)}",
                            progress = memPct?.toFloat(),
                            fillColor = MaterialTheme.colorScheme.primary,
                        )
                        MetricBar(
                            label = "Rootfs",
                            valueText = "${formatBytes(root?.used)} / ${formatBytes(root?.total)}",
                            progress = diskPct?.toFloat(),
                            fillColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (progress?.logLines?.isNotEmpty() == true) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(TechColors.Deck)
                                .border(1.dp, TechColors.Edge, RoundedCornerShape(2.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            val displayLines = progress.logLines.takeLast(4)
                            displayLines.forEachIndexed { idx, line ->
                                Text(
                                    text = "> $line",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (idx == displayLines.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                } else if (state == NodeRefreshState.PARSING) {
                    // PARSING stage stays as approved
                    val pct = (fillFraction * 100).toInt().coerceIn(0, 100)
                    Spacer(Modifier.height(8.dp))
                    MetricBar(
                        label = "PARSE PROGRESS",
                        valueText = "$pct%",
                        progress = fillFraction,
                        barHeight = 7.dp,
                        fillColor = TechColors.Amber,
                        trackColor = TechColors.Deck,
                        animationDurationMs = 500,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Actions: if privilege denied on 403, reuse plate area with privilege copy
                if (progress?.isPrivilegeDenied == true && !isBusy) {
                    TechPlate(railColor = TechColors.Amber) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "PACKAGE PRIVILEGES",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TechColors.Amber,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = PRIVILEGE_DENIED_COPY,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenNodeShell,
                                enabled = !anyJobActive,
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "OPEN NODE SHELL",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onRefreshApt,
                                enabled = !anyJobActive,
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "REFRESH APT LISTS",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }
                } else if (isRemoteUpgradeRemoved && snap.updateCount > 0 && !isBusy) {
                    TechPlate(railColor = TechColors.Amber) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "REMOTE UPGRADE",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TechColors.Amber,
                            )
                            Spacer(Modifier.height(4.dp))
                            val explainerText = when (sshAvailability) {
                                SshUpgradeAvailability.AVAILABLE ->
                                    "The remote upgrade API is not available on this server. PXMX upgrades it over SSH."
                                SshUpgradeAvailability.NO_SAVED_SECRET ->
                                    "No saved password for SSH. Reconnect with Save credentials on, or run the upgrade from the node shell."
                                SshUpgradeAvailability.API_TOKEN_AUTH ->
                                    "API token auth cannot perform SSH upgrades. Run the upgrade from the node shell."
                            }
                            Text(
                                text = explainerText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            if (sshAvailability == SshUpgradeAvailability.AVAILABLE) {
                                Button(
                                    onClick = onInstallViaSsh,
                                    enabled = !anyJobActive,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(2.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "INSTALL VIA SSH",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            OutlinedButton(
                                onClick = onOpenNodeShell,
                                enabled = !anyJobActive,
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "OPEN NODE SHELL",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onRefreshApt,
                                enabled = !anyJobActive,
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "REFRESH APT LISTS",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }
                } else if (snap.updateCount > 0 && !isBusy) {
                    Button(
                        onClick = onInstallUpdates,
                        enabled = !anyJobActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "INSTALL UPDATES (${snap.updateCount})",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRefreshApt,
                        enabled = !anyJobActive,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "REFRESH APT LISTS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                } else {
                    Button(
                        onClick = onRefreshApt,
                        enabled = !isBusy && !anyJobActive,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = when {
                                state == NodeRefreshState.UPGRADING -> "UPGRADING PACKAGES…"
                                isBusy -> "PARSING REPOSITORIES…"
                                else -> "REFRESH APT LISTS"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Live TechPlate package row in apt order. */
@Composable
private fun PackageUpdateRow(
    upd: AptPackageUpdate,
    node: String,
    isActive: Boolean,
    isUpgrading: Boolean = false,
) {
    val isSecurity = isSecurityUpdate(upd)
    val railColor = if (isSecurity) TechColors.Amber else TechColors.LinkGreen
    val shape = CutCornerShape(bottomEnd = 16.dp)

    val activeFill by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(800),
        label = "pkgFill",
    )

    val activeColor by animateColorAsState(
        targetValue = when {
            activeFill < 0.60f -> TechColors.LinkGreen
            activeFill < 0.85f -> TechColors.Amber
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(800),
        label = "pkgColor",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TechColors.Hull)
            .border(1.dp, if (isUpgrading) TechColors.Edge.copy(alpha = 0.5f) else TechColors.Edge, shape),
    ) {
        // Task 1: During UPGRADING, active package row sweeps background fill with threshold colors
        if (isActive && isUpgrading && activeFill > 0f) {
            ResourceFillBackground(
                animatedUsage = activeFill,
                fillColor = activeColor,
                modifier = Modifier.matchParentSize(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .alpha(if (isUpgrading && !isActive) 0.5f else 1.0f),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (isUpgrading && !isActive) TechColors.Mute else railColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val textColor = if (isUpgrading && !isActive) TechColors.Mute else MaterialTheme.colorScheme.onSurface
                    Text(
                        text = (upd.packageName ?: "?").uppercase(Locale.US),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                    )
                    StatusTag(
                        text = if (isSecurity) "SECURITY" else "UPDATABLE",
                        color = if (isUpgrading && !isActive) TechColors.Mute else railColor,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // "current → new" version line
                val verLine = buildString {
                    upd.oldVersion?.let { append(it) }
                    if (!upd.oldVersion.isNullOrBlank() && !upd.version.isNullOrBlank()) {
                        append("  →  ")
                    }
                    upd.version?.let { append(it) }
                }
                if (verLine.isNotBlank()) {
                    Text(
                        text = verLine,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                upd.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // During PARSING only: show MetricBar under package info
                if (isActive && !isUpgrading) {
                    val activePct = (activeFill * 100).toInt().coerceIn(0, 100)
                    MetricBar(
                        label = "PARSING PACKAGE",
                        valueText = "$activePct%",
                        progress = activeFill,
                        barHeight = 5.dp,
                        fillColor = railColor,
                        trackColor = TechColors.Deck,
                        animationDurationMs = 800,
                    )
                }

                Spacer(Modifier.height(2.dp))

                // Meta line: (SECURITY/REGULAR · size/arch/section · node)
                val kind = if (isSecurity) "SECURITY" else "REGULAR"
                val arch = upd.arch?.uppercase(Locale.US) ?: "AMD64"
                val sec = upd.section?.uppercase(Locale.US) ?: "ADMIN"
                val meta = "$kind · $arch · $sec · ${node.uppercase(Locale.US)}"
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusTag(
    text: String,
    color: Color,
) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun VersionCard(ver: AptPackageVersion) {
    TechPlate(railColor = TechColors.Edge) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (ver.packageName ?: "?").uppercase(Locale.US),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    ver.version ?: "—",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ver.currentState?.let {
                Text(
                    it.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
