package com.pxmx.app.ui.guest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.pxmx.app.data.model.BackupVolume
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.ConfigDisk
import com.pxmx.app.data.model.ConfigNet
import com.pxmx.app.data.model.ConfigUsb
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.ParsedGuestConfig
import com.pxmx.app.data.model.SnapshotInfo
import com.pxmx.app.ui.components.ExpandableSection
import com.pxmx.app.ui.components.MetricBar
import com.pxmx.app.ui.components.SystemLogStrip
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechIconBay
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechStatusPlate
import com.pxmx.app.ui.components.techRailColor
import com.pxmx.app.ui.guest.detail.*
import com.pxmx.app.ui.icons.GuestIcons
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatEpoch
import com.pxmx.app.ui.util.formatMemoryMiB
import com.pxmx.app.ui.util.formatPercent
import com.pxmx.app.ui.util.formatUptime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestDetailScreen(
    viewModel: GuestDetailViewModel,
    onBack: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenLogs: () -> Unit = {},
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val latestLog by viewModel.latestLog.collectAsStateWithLifecycle()
    val st = state.status
    val cfg = state.config
    val running = st?.status == "running"
    val busy = state.actionInProgress != null
    var powerMenu by remember { mutableStateOf(false) }

    // Back handling ordering: modal dialogs/menus close first, then pop screen back to Home
    BackHandler(enabled = powerMenu) {
        powerMenu = false
    }
    BackHandler(enabled = state.showCreateSnapshot) {
        viewModel.openCreateSnapshot(false)
    }
    BackHandler(enabled = state.showCreateBackup) {
        viewModel.openCreateBackup(false)
    }
    BackHandler(enabled = state.confirmDeleteSnap != null) {
        viewModel.confirmDeleteSnapshot(null)
    }
    BackHandler(enabled = state.confirmRollbackSnap != null) {
        viewModel.confirmRollback(null)
    }
    BackHandler(enabled = state.confirmDeleteBackup != null) {
        viewModel.confirmDeleteBackup(null)
    }
    BackHandler(
        enabled = !powerMenu &&
            !state.showCreateSnapshot &&
            !state.showCreateBackup &&
            state.confirmDeleteSnap == null &&
            state.confirmRollbackSnap == null &&
            state.confirmDeleteBackup == null
    ) {
        onBack()
    }

    val iconStyle = remember(state.name, state.guestType, cfg?.ostype, cfg?.tags) {
        GuestIcons.styleFor(
            ClusterResource(
                type = state.guestType.path,
                name = state.name,
                ostype = cfg?.ostype,
                tags = cfg?.tags ?: st?.tags,
                status = st?.status,
                vmid = state.vmid,
                node = state.node,
            ),
        )
    }

    Scaffold(
        bottomBar = {
            SystemLogStrip(
                entry = latestLog,
                onClick = {
                    com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.LOG_STRIP)
                    onOpenLogs()
                },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, maxLines = 1)
                        Text(
                            text = "${state.guestType.label} ${state.vmid} · ${state.node}",
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
                    IconButton(
                        onClick = onOpenConsole,
                        enabled = running && !busy,
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = "Console")
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(
                            onClick = {
                                powerMenu = true
                                com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                            },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                        }
                        DropdownMenu(
                            expanded = powerMenu,
                            onDismissRequest = {
                                powerMenu = false
                                com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                            },
                        ) {
                            availableActions(state.guestType, running, st?.status).forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        powerMenu = false
                                        com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                                        viewModel.runPowerAction(action)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && cfg == null) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(15.dp), // ~6% tighter than 16
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item(key = "snack") {
                AnimatedVisibility(
                    visible = state.message != null || state.error != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut(),
                ) {
                    val err = state.error
                    val msg = state.message
                    if (err != null) {
                        Snackbar { Text(err) }
                    } else if (msg != null) {
                        Snackbar { Text(msg) }
                    }
                }
            }

            // Section tabs — jump/expand; sized for touch, not tiny chips.
            item(key = "tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WeakTab(
                        label = "OPS",
                        selected = state.expanded == null,
                        onClick = { viewModel.selectSection(null) },
                    )
                    GuestSection.entries.forEach { section ->
                        WeakTab(
                            label = when (section) {
                                GuestSection.HARDWARE -> "HW"
                                GuestSection.NETWORK -> "NET"
                                GuestSection.OPTIONS -> "OPT"
                                GuestSection.SNAPSHOTS -> "SNAP"
                                GuestSection.BACKUPS -> "BKP"
                                GuestSection.RAW -> "RAW"
                            },
                            selected = state.expanded == section,
                            onClick = { viewModel.selectSection(section) },
                        )
                    }
                }
            }

            item(key = "hero") {
                HeroCard(
                    name = state.name,
                    status = st?.status,
                    icon = iconStyle.icon,
                    accent = iconStyle.accent,
                    kindLabel = iconStyle.label,
                    uptime = st?.uptime,
                    cpu = st?.cpu,
                    cpus = st?.cpus ?: cfg?.cores?.toIntOrNull(),
                    mem = st?.mem,
                    maxmem = st?.maxmem,
                    disk = st?.disk,
                    maxdisk = st?.maxdisk,
                    tags = st?.tags ?: cfg?.tags,
                    busy = busy,
                )
            }

            item(key = "power") {
                PowerCard(
                    running = running,
                    status = st?.status,
                    busy = busy,
                    actionInProgress = state.actionInProgress,
                    guestType = state.guestType,
                    onPower = {
                        com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                        viewModel.runPowerAction(it)
                    },
                    onOpenConsole = onOpenConsole,
                    onOpenPowerMenu = {
                        powerMenu = true
                        com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                    },
                )
            }

            item(key = "hardware") {
                val disks = cfg?.disks.orEmpty()
                ExpandableSection(
                    title = GuestSection.HARDWARE.title,
                    subtitle = "${disks.size} disk(s) · CPU ${cfg?.vcpus ?: "—"} · ${formatMemoryMiB(cfg?.memory)}",
                    icon = Icons.Default.Memory,
                    expanded = state.expanded == GuestSection.HARDWARE,
                    onToggle = { viewModel.toggleSection(GuestSection.HARDWARE) },
                ) {
                    HardwareBody(cfg)
                }
            }

            item(key = "network") {
                val nets = cfg?.nets.orEmpty()
                val usbs = cfg?.usbs.orEmpty()
                ExpandableSection(
                    title = GuestSection.NETWORK.title,
                    subtitle = "${nets.size} NIC(s) · ${usbs.size} USB · ${cfg?.pcis?.size ?: 0} PCI",
                    icon = Icons.Default.Cable,
                    expanded = state.expanded == GuestSection.NETWORK,
                    onToggle = { viewModel.toggleSection(GuestSection.NETWORK) },
                ) {
                    NetworkUsbBody(
                        cfg = cfg,
                        hostUsbs = state.hostUsbs,
                        isQemu = state.guestType == GuestType.QEMU,
                        busy = busy,
                        onDetach = { viewModel.detachUsb(it) },
                        onAttach = { id, usb3 -> viewModel.attachUsb(id, usb3) },
                        onRefreshUsb = { viewModel.refreshUsbOnly() },
                    )
                }
            }

            item(key = "options") {
                ExpandableSection(
                    title = GuestSection.OPTIONS.title,
                    subtitle = listOfNotNull(cfg?.ostype, cfg?.machine, cfg?.bios).joinToString(" · ")
                        .ifBlank { "Boot, OS, machine" },
                    icon = Icons.Default.Settings,
                    expanded = state.expanded == GuestSection.OPTIONS,
                    onToggle = { viewModel.toggleSection(GuestSection.OPTIONS) },
                ) {
                    OptionsBody(cfg)
                }
            }

            item(key = "snapshots") {
                val snapCount = state.snapshots.count { !it.isCurrent }
                ExpandableSection(
                    title = GuestSection.SNAPSHOTS.title,
                    subtitle = if (snapCount == 0) "No snapshots" else "$snapCount snapshot(s)",
                    icon = Icons.Default.Camera,
                    expanded = state.expanded == GuestSection.SNAPSHOTS,
                    onToggle = { viewModel.toggleSection(GuestSection.SNAPSHOTS) },
                    trailing = {
                        IconButton(
                            onClick = { viewModel.openCreateSnapshot(true) },
                            enabled = !busy,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create snapshot")
                        }
                    },
                ) {
                    SnapshotsBody(
                        snapshots = state.snapshots,
                        busy = busy,
                        onDelete = { viewModel.confirmDeleteSnapshot(it) },
                        onRollback = { viewModel.confirmRollback(it) },
                    )
                }
            }

            item(key = "backups") {
                ExpandableSection(
                    title = GuestSection.BACKUPS.title,
                    subtitle = when {
                        state.backupStorages.isEmpty() -> "No backup storage"
                        state.backups.isEmpty() -> "No backups yet"
                        else -> "${state.backups.size} backup(s)"
                    },
                    icon = Icons.Default.Backup,
                    expanded = state.expanded == GuestSection.BACKUPS,
                    onToggle = { viewModel.toggleSection(GuestSection.BACKUPS) },
                    trailing = {
                        IconButton(
                            onClick = { viewModel.openCreateBackup(true) },
                            enabled = !busy && state.backupStorages.isNotEmpty(),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Backup now")
                        }
                    },
                ) {
                    BackupsBody(
                        backups = state.backups,
                        busy = busy,
                        onDelete = { viewModel.confirmDeleteBackup(it) },
                    )
                }
            }

            item(key = "raw") {
                ExpandableSection(
                    title = GuestSection.RAW.title,
                    subtitle = "${cfg?.raw?.size ?: 0} keys",
                    icon = Icons.Default.Code,
                    expanded = state.expanded == GuestSection.RAW,
                    onToggle = { viewModel.toggleSection(GuestSection.RAW) },
                ) {
                    RawBody(cfg)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Dialogs
    if (state.showCreateSnapshot) {
        CreateSnapshotDialog(
            isQemu = state.guestType == GuestType.QEMU,
            onDismiss = { viewModel.openCreateSnapshot(false) },
            onConfirm = { n, d, r -> viewModel.createSnapshot(n, d, r) },
        )
    }
    if (state.showCreateBackup) {
        CreateBackupDialog(
            storages = state.backupStorages,
            onDismiss = { viewModel.openCreateBackup(false) },
            onConfirm = { s, m -> viewModel.createBackup(s, m) },
        )
    }
    state.confirmDeleteSnap?.let { snap ->
        ConfirmDialog(
            title = "Delete snapshot?",
            body = "Delete “$snap”? This cannot be undone.",
            confirm = "Delete",
            onDismiss = { viewModel.confirmDeleteSnapshot(null) },
            onConfirm = { viewModel.deleteSnapshot(snap) },
        )
    }
    state.confirmRollbackSnap?.let { snap ->
        ConfirmDialog(
            title = "Rollback snapshot?",
            body = "Roll back to “$snap”? Guest should usually be stopped first.",
            confirm = "Rollback",
            onDismiss = { viewModel.confirmRollback(null) },
            onConfirm = { viewModel.rollbackSnapshot(snap) },
        )
    }
    state.confirmDeleteBackup?.let { vol ->
        ConfirmDialog(
            title = "Delete backup?",
            body = "Delete ${vol.volid}?",
            confirm = "Delete",
            onDismiss = { viewModel.confirmDeleteBackup(null) },
            onConfirm = { viewModel.deleteBackup(vol) },
        )
    }
}

@Composable
private fun WeakTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(bottomEnd = 10.dp)
    val border = if (selected) MaterialTheme.colorScheme.primary else TechColors.Edge
    val bg = if (selected) TechColors.Deck else TechColors.Hull
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, border, shape)
            .background(bg, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun HeroCard(
    name: String,
    status: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    kindLabel: String,
    uptime: Long?,
    cpu: Double?,
    cpus: Int?,
    mem: Long?,
    maxmem: Long?,
    disk: Long?,
    maxdisk: Long?,
    tags: String?,
    busy: Boolean,
) {
    TechPlate(railColor = techRailColor(status, busy)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            TechIconBay(
                icon = icon,
                accent = accent,
                contentDescription = kindLabel,
                size = 48.dp,
                iconSize = 28.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                )
                Text(
                    text = kindLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TechStatusPlate(status = status)
        }

        TechDeck(showAccentBar = true) {
            MetricBar(
                label = "CPU" + (cpus?.let { " · $it vCPU" } ?: ""),
                valueText = formatPercent(cpu?.times(100)),
                progress = cpu?.toFloat()?.coerceIn(0f, 1f),
            )
            val memPct = if (mem != null && maxmem != null && maxmem > 0) {
                (mem.toDouble() / maxmem.toDouble()).toFloat().coerceIn(0f, 1f)
            } else null
            MetricBar(
                label = "Memory",
                valueText = "${formatBytes(mem)} / ${formatBytes(maxmem)}",
                progress = memPct,
            )
            val diskPct = if (disk != null && maxdisk != null && maxdisk > 0) {
                (disk.toDouble() / maxdisk.toDouble()).toFloat().coerceIn(0f, 1f)
            } else null
            MetricBar(
                label = "Disk",
                valueText = "${formatBytes(disk)} / ${formatBytes(maxdisk)}",
                progress = diskPct,
            )

            Spacer(Modifier.height(6.dp))
            TechMetaLine("Uptime", formatUptime(uptime))
            tags?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                TechMetaLine("Tags", it.replace(";", ", ").replace(",", ", "))
            }
            if (busy) {
                Spacer(Modifier.height(8.dp))
                LinearBusy()
            }
        }
    }
}

@Composable
private fun LinearBusy() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Working…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PowerCard(
    running: Boolean,
    status: String?,
    busy: Boolean,
    actionInProgress: String?,
    guestType: GuestType,
    onPower: (GuestAction) -> Unit,
    onOpenConsole: () -> Unit,
    onOpenPowerMenu: () -> Unit = {},
) {
    val actions = availableActions(guestType, running, status)
    TechPlate(
        railColor = if (running) TechColors.Amber else TechColors.Edge,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = "CONSOLE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onOpenConsole,
                enabled = running && !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(2.dp),
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "OPEN CONSOLE" else "CONSOLE (START GUEST FIRST)",
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                )
            }
            Text(
                if (guestType == GuestType.QEMU) "NOVNC · LIVE FROM SERVER"
                else "XTERM.JS · LIVE FROM SERVER",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        TechDeck(showAccentBar = true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPowerMenu() }
                    .padding(vertical = 4.dp),
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "POWER",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (GuestAction.START in actions) {
                    Button(
                        onClick = { onPower(GuestAction.START) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        ActionLabel(GuestAction.START, actionInProgress == "power:start")
                    }
                }
                if (GuestAction.SHUTDOWN in actions) {
                    Button(
                        onClick = { onPower(GuestAction.SHUTDOWN) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        ActionLabel(GuestAction.SHUTDOWN, actionInProgress == "power:shutdown")
                    }
                }
                if (GuestAction.REBOOT in actions) {
                    OutlinedButton(
                        onClick = { onPower(GuestAction.REBOOT) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        ActionLabel(GuestAction.REBOOT, actionInProgress == "power:reboot")
                    }
                }
            }
            if (actions.any { it in listOf(GuestAction.STOP, GuestAction.RESET, GuestAction.SUSPEND, GuestAction.RESUME) }) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (GuestAction.STOP in actions) {
                        OutlinedButton(
                            onClick = { onPower(GuestAction.STOP) },
                            enabled = !busy,
                            shape = RoundedCornerShape(2.dp),
                        ) { ActionLabel(GuestAction.STOP, actionInProgress == "power:stop") }
                    }
                    if (GuestAction.RESET in actions) {
                        OutlinedButton(
                            onClick = { onPower(GuestAction.RESET) },
                            enabled = !busy,
                            shape = RoundedCornerShape(2.dp),
                        ) { ActionLabel(GuestAction.RESET, actionInProgress == "power:reset") }
                    }
                    if (GuestAction.SUSPEND in actions) {
                        OutlinedButton(
                            onClick = { onPower(GuestAction.SUSPEND) },
                            enabled = !busy,
                            shape = RoundedCornerShape(2.dp),
                        ) { ActionLabel(GuestAction.SUSPEND, actionInProgress == "power:suspend") }
                    }
                    if (GuestAction.RESUME in actions) {
                        OutlinedButton(
                            onClick = { onPower(GuestAction.RESUME) },
                            enabled = !busy,
                            shape = RoundedCornerShape(2.dp),
                        ) { ActionLabel(GuestAction.RESUME, actionInProgress == "power:resume") }
                    }
                }
            }
            Text(
                "MORE ACTIONS · ⋮ MENU",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { onOpenPowerMenu() },
            )
        }
    }
}

@Composable
private fun ActionLabel(action: GuestAction, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(6.dp))
    }
    Text(action.label)
}
