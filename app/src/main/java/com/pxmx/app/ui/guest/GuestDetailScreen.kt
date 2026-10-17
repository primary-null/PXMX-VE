package com.pxmx.app.ui.guest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import com.pxmx.app.data.util.TagUtils
import com.pxmx.app.ui.components.AddTagButton
import com.pxmx.app.ui.components.TagChip
import com.pxmx.app.ui.components.TechHistoryGraph
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import com.pxmx.app.data.ops.OpsBoard
import com.pxmx.app.data.ops.clipIdToSection
import com.pxmx.app.data.ops.guestDetailLazyKeys
import com.pxmx.app.data.ops.itemKey
import com.pxmx.app.data.ops.sectionToClipId
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.pxmx.app.ProxmoxApp
import com.pxmx.app.data.console.ConsoleStill
import com.pxmx.app.data.console.ConsoleStillStore
import com.pxmx.app.ui.components.TechActionPlate
import com.pxmx.app.ui.components.neonSelectionHalo
import com.pxmx.app.ui.extras.stupidChipFlip
import com.pxmx.app.ui.extras.stupidSectionFold
import com.pxmx.app.ui.extras.stupidTabDeckYaw
import com.pxmx.app.ui.icons.InstrumentGlyphs
import com.pxmx.app.ui.util.Toasts
import com.pxmx.app.ui.util.ToastKind
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pxmx.app.data.model.WatchIds
import com.pxmx.app.ui.permissions.RequestPostNotificationsOnUse
import com.pxmx.app.ui.watch.WatchEye
import com.pxmx.app.ui.watch.rememberWatchToggle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import android.os.SystemClock
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
import com.pxmx.app.ui.components.MetricBar
import com.pxmx.app.ui.components.SystemLogStrip
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.rememberReduceMotion
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechDropdownMenu
import com.pxmx.app.ui.components.TechMenuHeader
import com.pxmx.app.ui.components.TechMenuItem
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechPlateShape
import com.pxmx.app.ui.components.TechStatusPlate
import com.pxmx.app.ui.components.techRailColor
import com.pxmx.app.ui.guest.detail.*
import com.pxmx.app.ui.icons.GuestIcons
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatDiskProgress
import com.pxmx.app.ui.util.formatDiskUsage
import com.pxmx.app.ui.util.formatEpoch
import com.pxmx.app.ui.util.formatMemoryMiB
import com.pxmx.app.ui.util.formatPercent
import com.pxmx.app.ui.util.formatUptime

private data class BezelTab(
    val label: String,
    val fullLabel: String,
    val slug: String,
    val section: GuestSection,
)

private val BEZEL_TABS = listOf(
    BezelTab(label = "HW", fullLabel = "HARDWARE", slug = "hw", section = GuestSection.HARDWARE),
    BezelTab(label = "NET", fullLabel = "NETWORK", slug = "net", section = GuestSection.NETWORK),
    BezelTab(label = "FW", fullLabel = "FIREWALL", slug = "fw", section = GuestSection.FIREWALL),
    BezelTab(label = "SNAP", fullLabel = "SNAPSHOTS", slug = "snap", section = GuestSection.SNAPSHOTS),
    BezelTab(label = "BKP", fullLabel = "BACKUPS", slug = "bkp", section = GuestSection.BACKUPS),
    BezelTab(label = "OPT", fullLabel = "OPTIONS", slug = "opt", section = GuestSection.OPTIONS),
    BezelTab(label = "RAW", fullLabel = "RAW", slug = "raw", section = GuestSection.RAW),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestDetailScreen(
    viewModel: GuestDetailViewModel,
    onBack: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenLogs: () -> Unit = {},
    isEmbeddedInPane: Boolean = false,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val watchedIds by viewModel.watchedIds.collectAsStateWithLifecycle()
    val onWatchClick = rememberWatchToggle(viewModel::setWatched)
    val latestLog by viewModel.latestLog.collectAsStateWithLifecycle()
    val st = state.status
    val cfg = state.config
    val running = st?.status == "running"
    val busy = state.actionInProgress != null
    var powerMenu by remember { mutableStateOf(false) }
    var pendingDanger by remember { mutableStateOf<GuestAction?>(null) }
    var armNotificationPrompt by rememberSaveable { mutableStateOf(false) }

    RequestPostNotificationsOnUse(
        armed = armNotificationPrompt,
        onPrompted = { armNotificationPrompt = false },
    )

    val haptic = LocalHapticFeedback.current
    val dispatchPower: (GuestAction) -> Unit = { action ->
        armNotificationPrompt = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.runPowerAction(action)
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrBlank()) {
            Toasts.show(context, msg, ToastKind.INFO)
            viewModel.consumeMessage(msg)
        }
    }
    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank()) {
            Toasts.show(context, err, ToastKind.ERROR)
            viewModel.consumeError(err)
        }
    }

    val stillStore = (context.applicationContext as? ProxmoxApp)?.consoleStillStore
    val stillsFlow = remember(stillStore) {
        stillStore?.stills ?: kotlinx.coroutines.flow.flowOf(emptyMap())
    }
    val stills by stillsFlow.collectAsStateWithLifecycle(emptyMap())
    val profileId = remember(state) { viewModel.currentProfileId() }
    val stillKey = remember(profileId, state.guestType, state.vmid) {
        ConsoleStillStore.stillKey(profileId, state.guestType, state.vmid)
    }
    val currentStill = if (running) stills[stillKey] else null

    // Back handling ordering: modal dialogs/menus close first, then pop screen back to Home
    BackHandler(enabled = pendingDanger != null) {
        pendingDanger = null
    }
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
    BackHandler(enabled = state.isEditingBoard) {
        viewModel.toggleEditBoard()
    }
    BackHandler(
        enabled = pendingDanger == null &&
            !powerMenu &&
            !state.isEditingBoard &&
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
            if (!isEmbeddedInPane) {
                SystemLogStrip(
                    entry = latestLog,
                    onClick = {
                        com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.LOG_STRIP)
                        onOpenLogs()
                    },
                )
            }
        },
        topBar = {
            TopAppBar(
                colors = com.pxmx.app.ui.components.techTopAppBarColors(),
                title = {
                    Column {
                        Text(
                            text = state.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${state.guestType.label} ${state.vmid} · ${state.node}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = if (isEmbeddedInPane) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEmbeddedInPane) "Close" else "Back",
                        )
                    }
                },
                actions = {
                    val watchId = WatchIds.guest(state.node, state.vmid)
                    if (WatchIds.isValid(watchId)) {
                        WatchEye(
                            watched = watchId in watchedIds,
                            label = state.name,
                            onClick = { onWatchClick(watchId, watchId in watchedIds) },
                        )
                    }
                    IconButton(
                        onClick = onOpenConsole,
                        enabled = running && !busy,
                    ) {
                        Icon(InstrumentGlyphs.Console, contentDescription = "Console", tint = Color.Unspecified)
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !busy) {
                        Icon(InstrumentGlyphs.Refresh, contentDescription = "Refresh", tint = Color.Unspecified)
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
                        TechDropdownMenu(
                            expanded = powerMenu,
                            onDismissRequest = {
                                powerMenu = false
                                com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                            },
                        ) {
                            TechMenuHeader("PWR · ${(st?.status ?: "unknown")}")
                            if (running) {
                                TechMenuItem(
                                    label = "Console",
                                    hint = "noVNC / xterm",
                                    leadingIcon = Icons.Default.Computer,
                                    onClick = {
                                        powerMenu = false
                                        onOpenConsole()
                                    },
                                )
                            }
                            availableActions(state.guestType, running, st?.status).forEach { action ->
                                val danger = action == GuestAction.STOP || action == GuestAction.RESET
                                TechMenuItem(
                                    label = action.label,
                                    hint = powerHint(action),
                                    danger = danger,
                                    enabled = !busy,
                                    onClick = {
                                        powerMenu = false
                                        com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                                        if (danger) pendingDanger = action
                                        else dispatchPower(action)
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

        val reduceMotion = rememberReduceMotion()
        val haptic = LocalHapticFeedback.current
        val wiggleElapsedMs = remember(state.isEditingBoard, reduceMotion) {
            mutableLongStateOf(0L)
        }
        LaunchedEffect(state.isEditingBoard, reduceMotion) {
            if (!state.isEditingBoard || reduceMotion) {
                wiggleElapsedMs.longValue = 0L
                return@LaunchedEffect
            }
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { frameTime ->
                    wiggleElapsedMs.longValue = frameTime - start
                }
            }
        }

        val keysInLazyColumn = remember(state.isEditingBoard, state.appliedKeys) {
            guestDetailLazyKeys(state.isEditingBoard, state.appliedKeys)
        }

        LaunchedEffect(state.expanded) {
            val sec = state.expanded
            if (sec != null) {
                val target = keysInLazyColumn.indexOf(sec.itemKey())
                if (target >= 0) {
                    listState.animateScrollToItem(target)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 1. Sticky bezel row (NOT inside LazyColumn)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 6.dp),
            ) {
                BezelHugRow(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    keysInLazyColumn = keysInLazyColumn,
                )
            }

            // 2. Continuous OPS deck in LazyColumn
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .stupidTabDeckYaw(state.expanded?.name ?: "ops"),
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 15.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (state.isEditingBoard) {
                    item(key = "edit_board") {
                        EditBoardCard(
                            board = state.board,
                            onMoveUp = { viewModel.moveClipUp(it) },
                            onMoveDown = { viewModel.moveClipDown(it) },
                            onTogglePin = { viewModel.togglePinSection(it) },
                            onToggleConsole = { viewModel.toggleConsole(it) },
                            onToggleHistory = { viewModel.toggleHistory(it) },
                            onSaveAsDefault = { viewModel.saveBoardAsProfileDefault() },
                            onClose = { viewModel.toggleEditBoard() },
                            elapsedMs = { wiggleElapsedMs.longValue },
                            reduceMotion = reduceMotion,
                        )
                    }
                }

                state.appliedKeys.forEachIndexed { index, key ->
                    when (key) {
                        "hero" -> item(key = "hero") {
                            HeroCard(
                                name = state.name,
                                status = st?.status,
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
                                vmid = state.vmid,
                                onAddTag = { viewModel.addTag(it) },
                                onRemoveTag = { viewModel.removeTag(it) },
                            )
                        }
                        "hardware" -> item(key = "hardware") {
                            MovableDeckItem(
                                key = "hardware",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                val disks = cfg?.disks?.size ?: 0
                                val cores = cfg?.cores ?: "—"
                                val mem = formatMemoryMiB(cfg?.memory)
                                GuestSectionPlate(
                                    title = GuestSection.HARDWARE.title,
                                    subtitle = "${disks}d · ${cores}c · $mem",
                                    expanded = state.expanded == GuestSection.HARDWARE,
                                    onToggle = { viewModel.toggleSection(GuestSection.HARDWARE) },
                                    showPin = true,
                                    isPinned = state.board.isPinned(GuestSection.HARDWARE),
                                    onTogglePin = { viewModel.togglePinSection(GuestSection.HARDWARE) },
                                ) {
                                    HardwareBody(cfg)
                                }
                            }
                        }
                        "network" -> item(key = "network") {
                            MovableDeckItem(
                                key = "network",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                val nics = cfg?.nets?.size ?: 0
                                val usbs = cfg?.usbs?.size ?: 0
                                GuestSectionPlate(
                                    title = GuestSection.NETWORK.title,
                                    subtitle = "$nics nic · $usbs usb",
                                    expanded = state.expanded == GuestSection.NETWORK,
                                    onToggle = { viewModel.toggleSection(GuestSection.NETWORK) },
                                    showPin = true,
                                    isPinned = state.board.isPinned(GuestSection.NETWORK),
                                    onTogglePin = { viewModel.togglePinSection(GuestSection.NETWORK) },
                                ) {
                                    NetworkUsbBody(
                                        cfg = cfg,
                                        hostUsbs = state.hostUsbs,
                                        liveInterfaces = state.liveInterfaces,
                                        firewall = state.firewall,
                                        isQemu = state.guestType == GuestType.QEMU,
                                        busy = busy,
                                        onDetach = { viewModel.detachUsb(it) },
                                        onAttach = { id, usb3 -> viewModel.attachUsb(id, usb3) },
                                        onRefreshUsb = { viewModel.refreshUsbOnly() },
                                        onToggleFirewall = viewModel::toggleNicFirewall,
                                        onToggleLinkDown = viewModel::toggleNicLinkDown,
                                    )
                                }
                            }
                        }
                        "firewall" -> item(key = "firewall") {
                            MovableDeckItem(
                                key = "firewall",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                val fw = state.firewall
                                val fwErr = state.firewallError
                                val fwSubtitle = when {
                                    fwErr != null -> "err"
                                    fw == null -> "—"
                                    fw.enabled -> "on · ${fw.rules.size}"
                                    else -> "off · ${fw.rules.size}"
                                }
                                GuestSectionPlate(
                                    title = GuestSection.FIREWALL.title,
                                    subtitle = fwSubtitle,
                                    expanded = state.expanded == GuestSection.FIREWALL,
                                    onToggle = { viewModel.toggleSection(GuestSection.FIREWALL) },
                                    showPin = true,
                                    isPinned = state.board.isPinned(GuestSection.FIREWALL),
                                    onTogglePin = { viewModel.togglePinSection(GuestSection.FIREWALL) },
                                ) {
                                    FirewallBody(
                                        snapshot = state.firewall,
                                        error = state.firewallError,
                                    )
                                }
                            }
                        }
                        "snapshots" -> item(key = "snapshots") {
                            MovableDeckItem(
                                key = "snapshots",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                val snapCount = state.snapshots.count { !it.isCurrent }
                                GuestSectionPlate(
                                    title = GuestSection.SNAPSHOTS.title,
                                    subtitle = "$snapCount",
                                    expanded = state.expanded == GuestSection.SNAPSHOTS,
                                    onToggle = { viewModel.toggleSection(GuestSection.SNAPSHOTS) },
                                    showPin = true,
                                    isPinned = state.board.isPinned(GuestSection.SNAPSHOTS),
                                    onTogglePin = { viewModel.togglePinSection(GuestSection.SNAPSHOTS) },
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
                        }
                        "backups" -> item(key = "backups") {
                            MovableDeckItem(
                                key = "backups",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                val bkpSubtitle = if (state.backups.isEmpty()) "none" else "${state.backups.size}"
                                GuestSectionPlate(
                                    title = GuestSection.BACKUPS.title,
                                    subtitle = bkpSubtitle,
                                    expanded = state.expanded == GuestSection.BACKUPS,
                                    onToggle = { viewModel.toggleSection(GuestSection.BACKUPS) },
                                    showPin = true,
                                    isPinned = state.board.isPinned(GuestSection.BACKUPS),
                                    onTogglePin = { viewModel.togglePinSection(GuestSection.BACKUPS) },
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
                        }
                        "options" -> item(key = "options") {
                            MovableDeckItem(
                                key = "options",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                val optSubtitle = cfg?.ostype?.takeIf { it.isNotBlank() } ?: "boot"
                                GuestSectionPlate(
                                    title = GuestSection.OPTIONS.title,
                                    subtitle = optSubtitle,
                                    expanded = state.expanded == GuestSection.OPTIONS,
                                    onToggle = { viewModel.toggleSection(GuestSection.OPTIONS) },
                                    showPin = true,
                                    isPinned = state.board.isPinned(GuestSection.OPTIONS),
                                    onTogglePin = { viewModel.togglePinSection(GuestSection.OPTIONS) },
                                ) {
                                    OptionsBody(cfg)
                                }
                            }
                        }
                        "console" -> item(key = "console") {
                            MovableDeckItem(
                                key = "console",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                ConsoleCard(
                                    running = running,
                                    busy = busy,
                                    guestType = state.guestType,
                                    onOpenConsole = onOpenConsole,
                                    isTokenSession = state.isTokenSession,
                                    still = currentStill,
                                )
                            }
                        }
                        "history-graph" -> item(key = "history-graph") {
                            MovableDeckItem(
                                key = "history-graph",
                                index = index,
                                isEditing = state.isEditingBoard,
                                reduceMotion = reduceMotion,
                                elapsedMs = { wiggleElapsedMs.longValue },
                                onMoveUp = { viewModel.moveClipUp(it) },
                                onMoveDown = { viewModel.moveClipDown(it) },
                                haptic = haptic,
                            ) {
                                TechHistoryGraph(
                                    points = state.rrdData,
                                    isGuest = true,
                                    timeframe = state.rrdTimeframe,
                                    onTimeframe = { viewModel.setRrdTimeframe(it) },
                                )
                            }
                        }
                        "raw" -> item(key = "raw") {
                            GuestSectionPlate(
                                title = GuestSection.RAW.title,
                                subtitle = "${cfg?.raw?.size ?: 0} keys",
                                expanded = state.expanded == GuestSection.RAW,
                                onToggle = { viewModel.toggleSection(GuestSection.RAW) },
                                showPin = false,
                            ) {
                                RawBody(cfg)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Dialogs
    if (state.showCreateSnapshot) {
        CreateSnapshotDialog(
            isQemu = state.guestType == GuestType.QEMU,
            onDismiss = { viewModel.openCreateSnapshot(false) },
            onConfirm = { n, d, r ->
                armNotificationPrompt = true
                viewModel.createSnapshot(n, d, r)
            },
        )
    }
    if (state.showCreateBackup) {
        CreateBackupDialog(
            storages = state.backupStorages,
            onDismiss = { viewModel.openCreateBackup(false) },
            onConfirm = { s, m ->
                armNotificationPrompt = true
                viewModel.createBackup(s, m)
            },
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
            onConfirm = {
                armNotificationPrompt = true
                viewModel.rollbackSnapshot(snap)
            },
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
    pendingDanger?.let { action ->
        ConfirmDialog(
            title = "${action.label}?",
            body = when (action) {
                GuestAction.STOP -> "Hard power off. Unsaved work in the guest may be lost."
                GuestAction.RESET -> "Cycle power immediately. Same risk as a reset button."
                else -> "Run ${action.label}?"
            },
            confirm = action.label,
            onDismiss = { pendingDanger = null },
            onConfirm = {
                val run = action
                pendingDanger = null
                armNotificationPrompt = true
                dispatchPower(run)
            },
        )
    }
}

private fun powerHint(action: GuestAction): String = when (action) {
    GuestAction.START -> "boot the guest"
    GuestAction.SHUTDOWN -> "ACPI poweroff"
    GuestAction.STOP -> "hard power off"
    GuestAction.REBOOT -> "ACPI reboot"
    GuestAction.RESET -> "cycle power now"
    GuestAction.SUSPEND -> "pause / hibernate"
    GuestAction.RESUME -> "continue"
}

private class MeasureLogState {
    var lastS: Int = -1
    var lastT: Int = -1
    var lastE: Int = -1
    var lastM: Int = -1
    var lastC: Int = -1
    var lastMax: Int = -1
    var lastPath: String = ""
}

private enum class BezelSlot {
    EDIT,
    STAMPS_MEASURE,
    HULL_HUG,
    HULL_OVERFLOW,
}

@Composable
@Suppress("FunctionNaming", "LongParameterList")
private fun BezelStamps(
    state: GuestDetailUiState,
    viewModel: GuestDetailViewModel,
    listState: LazyListState,
    keysInLazyColumn: List<String>,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val haptic = LocalHapticFeedback.current
    val liftPx = with(density) { 14.dp.toPx() }
    val outwardNextPx = with(density) { 8.dp.toPx() }
    val outwardFarPx = with(density) { 4.dp.toPx() }
    val stampCount = 1 + BEZEL_TABS.size
    val spans = remember(stampCount) { Array(stampCount) { 0f..1f } }
    var scrubIndex by remember { mutableStateOf(-1) }
    val latestKeys = rememberUpdatedState(keysInLazyColumn)
    val jumpTo = rememberUpdatedState<(Int) -> Unit> { index ->
        if (index <= 0) {
            viewModel.selectSection(null)
            coroutineScope.launch {
                val heroIndex = latestKeys.value.indexOf("hero").coerceAtLeast(0)
                listState.animateScrollToItem(heroIndex)
            }
        } else {
            val tab = BEZEL_TABS.getOrNull(index - 1) ?: return@rememberUpdatedState
            viewModel.selectSection(tab.section)
            coroutineScope.launch {
                val targetIndex = latestKeys.value.indexOf(tab.section.itemKey())
                if (targetIndex >= 0) {
                    listState.animateScrollToItem(targetIndex)
                }
            }
        }
    }

    Row(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .padding(end = 28.dp)
            .pointerInput(state.isEditingBoard) {
                if (state.isEditingBoard) return@pointerInput
                val slopPx = 12.dp.toPx()
                val escapePx = 24.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val origin = down.position
                    val pointerId = down.id
                    var scrubbing = false
                    var cancelled = false
                    var armed = bezelScrubIndex(origin.x, spans.asList())
                    var armedHaptic = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.find { it.id == pointerId }
                            if (change == null) {
                                cancelled = true
                                break
                            }
                            val dx = change.position.x - origin.x
                            val dy = change.position.y - origin.y
                            val held = change.uptimeMillis - down.uptimeMillis
                            if (!scrubbing) {
                                val slide = kotlin.math.abs(dx)
                                if (bezelScrubShouldYieldToScroll(held, slide, slopPx) ||
                                    kotlin.math.abs(dy) >= slopPx
                                ) {
                                    cancelled = true
                                    break
                                }
                                if (bezelScrubShouldArm(held, slide, slopPx)) {
                                    scrubbing = true
                                    armed = bezelScrubIndex(change.position.x, spans.asList())
                                    scrubIndex = armed
                                    if (!armedHaptic) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        armedHaptic = true
                                    }
                                }
                            }
                            if (scrubbing) {
                                if (bezelScrubShouldCancel(dx, dy, escapePx)) {
                                    cancelled = true
                                    change.consume()
                                    break
                                }
                                change.consume()
                                val next = bezelScrubIndex(change.position.x, spans.asList())
                                if (next != armed) {
                                    armed = next
                                    scrubIndex = next
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        scrubIndex = -1
                    }
                    if (scrubbing && !cancelled) {
                        jumpTo.value.invoke(armed)
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BezelStamp(
            index = 0,
            scrubIndex = scrubIndex,
            reduceMotion = reduceMotion,
            liftPx = liftPx,
            outwardNextPx = outwardNextPx,
            outwardFarPx = outwardFarPx,
            spans = spans,
            label = "OPS",
            fullLabel = "OPS",
            selected = state.expanded == null && !state.isEditingBoard,
            onClick = { jumpTo.value.invoke(0) },
            modifier = Modifier.testTag("guest_bezel_ops"),
        )
        BEZEL_TABS.forEachIndexed { tabIndex, tab ->
            val index = tabIndex + 1
            BezelStamp(
                index = index,
                scrubIndex = scrubIndex,
                reduceMotion = reduceMotion,
                liftPx = liftPx,
                outwardNextPx = outwardNextPx,
                outwardFarPx = outwardFarPx,
                spans = spans,
                label = tab.label,
                fullLabel = tab.fullLabel,
                selected = state.expanded == tab.section && !state.isEditingBoard,
                onClick = {
                    if (state.expanded == tab.section) {
                        jumpTo.value.invoke(0)
                    } else {
                        jumpTo.value.invoke(index)
                    }
                },
                modifier = Modifier.testTag("guest_bezel_${tab.slug}"),
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun BezelStamp(
    index: Int,
    scrubIndex: Int,
    reduceMotion: Boolean,
    liftPx: Float,
    outwardNextPx: Float,
    outwardFarPx: Float,
    spans: Array<ClosedFloatingPointRange<Float>>,
    label: String,
    fullLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warp = bezelScrubWarp(
        index = index,
        armedIndex = scrubIndex,
        reduceMotion = reduceMotion,
        liftPx = liftPx,
        outwardNextPx = outwardNextPx,
        outwardFarPx = outwardFarPx,
    )
    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInParent()
                spans[index] = bounds.left..bounds.right
            }
            .graphicsLayer {
                scaleX = warp.scale
                scaleY = warp.scale
                translationX = warp.translationX
                translationY = warp.translationY
                rotationZ = warp.rotationZ
            },
    ) {
        WeakTab(
            label = label,
            fullLabel = fullLabel,
            selected = selected,
            armed = scrubIndex == index && !selected,
            onClick = onClick,
            modifier = modifier,
            embedded = true,
        )
    }
}

@Composable
@Suppress("FunctionNaming", "LongParameterList")
private fun BezelHull(
    state: GuestDetailUiState,
    viewModel: GuestDetailViewModel,
    listState: LazyListState,
    keysInLazyColumn: List<String>,
    coroutineScope: CoroutineScope,
    scrollState: ScrollState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(TechPlateShape)
            .background(TechColors.Hull)
            .border(1.dp, TechColors.Edge, TechPlateShape),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(TechColors.Edge),
            )
            Box(modifier = Modifier.fillMaxHeight().clipToBounds()) {
                val rowModifier = if (scrollState != null) {
                    Modifier.horizontalScroll(scrollState)
                } else {
                    Modifier
                }
                BezelStamps(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    keysInLazyColumn = keysInLazyColumn,
                    coroutineScope = coroutineScope,
                    modifier = rowModifier,
                )

                if (scrollState != null && scrollState.maxValue > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 28.dp)
                            .width(20.dp)
                            .fillMaxHeight()
                            .zIndex(0.5f)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, TechColors.Hull),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun BezelEditButton(
    state: GuestDetailUiState,
    viewModel: GuestDetailViewModel,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cutPx = with(density) { 28.dp.toPx() }
    val bleedPx = with(density) { 2.dp.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    val editBorder = if (state.isEditingBoard) MaterialTheme.colorScheme.primary else TechColors.Edge
    val editBg = if (state.isEditingBoard) TechColors.Deck else TechColors.Hull
    val editTextColor = if (state.isEditingBoard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .zIndex(1f)
            .then(
                if (state.isEditingBoard) {
                    Modifier.neonSelectionHalo(
                        enabled = true,
                        color = editBorder,
                        pathProvider = { s -> editStrokePath(s, cutPx, strokePx) },
                    )
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val fillPath = editJoinPath(size, cutPx, bleedPx)
                drawPath(fillPath, color = editBg)

                val strokePath = editStrokePath(size, cutPx, strokePx)
                drawPath(
                    path = strokePath,
                    color = editBorder,
                    style = Stroke(
                        width = strokePx,
                        join = StrokeJoin.Miter,
                    ),
                )
            }
            .semantics {
                role = Role.Tab
                this.selected = state.isEditingBoard
            }
            .clickable {
                viewModel.toggleEditBoard()
                if (!state.isEditingBoard) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            }
            .padding(start = 28.dp + 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("guest_bezel_edit"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "EDIT",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
            color = editTextColor,
        )
    }
}

@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun BezelHugRow(
    state: GuestDetailUiState,
    viewModel: GuestDetailViewModel,
    listState: LazyListState,
    keysInLazyColumn: List<String>,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val logState = remember { MeasureLogState() }

    SubcomposeLayout(modifier = modifier) { constraints ->
        val m = 28.dp.roundToPx()
        val railPx = 5.dp.roundToPx()

        // 1. Subcompose EDIT (wrap constraints minWidth = 0, maxWidth = Infinity)
        val editMeasurables = subcompose(BezelSlot.EDIT) {
            BezelEditButton(
                state = state,
                viewModel = viewModel,
                listState = listState,
                coroutineScope = coroutineScope,
            )
        }

        // 2. Subcompose ONLY the stamp Row (no rail, no clip, no Hull background)
        val stampMeasurables = subcompose(BezelSlot.STAMPS_MEASURE) {
            BezelStamps(
                state = state,
                viewModel = viewModel,
                listState = listState,
                keysInLazyColumn = keysInLazyColumn,
                coroutineScope = coroutineScope,
            )
        }
        val unconstrainedConstraints = Constraints(
            minWidth = 0,
            maxWidth = Constraints.Infinity,
            minHeight = 0,
            maxHeight = constraints.maxHeight,
        )
        val stampPlaceables = stampMeasurables.map { it.measure(unconstrainedConstraints) }
        val t = stampPlaceables.maxOfOrNull { it.width } ?: 0
        val stampH = stampPlaceables.maxOfOrNull { it.height } ?: 0

        // 3. S = T + 5.dp.roundToPx()
        val s = t + railPx

        // Measure EDIT with wrap width (maxWidth = Infinity) and height matching stamps natural height
        val targetHeight = if (stampH > 0) stampH else 0
        val editConstraints = Constraints(
            minWidth = 0,
            maxWidth = Constraints.Infinity,
            minHeight = targetHeight,
            maxHeight = if (stampH > 0) stampH else constraints.maxHeight,
        )
        val editPlaceables = editMeasurables.map { it.measure(editConstraints) }
        val e = editPlaceables.maxOfOrNull { it.width } ?: 0
        val editH = editPlaceables.maxOfOrNull { it.height } ?: 0

        val h = maxOf(stampH, editH).coerceIn(constraints.minHeight, constraints.maxHeight)

        // Remaining cap C = (constraints.maxWidth - E + M).coerceAtLeast(0)
        val c = (constraints.maxWidth - e + m).coerceAtLeast(0)

        val path = if (s <= c) "hug" else "overflow"
        if (logState.lastS != s || logState.lastT != t || logState.lastE != e ||
            logState.lastM != m || logState.lastC != c || logState.lastMax != constraints.maxWidth ||
            logState.lastPath != path
        ) {
            logState.lastS = s
            logState.lastT = t
            logState.lastE = e
            logState.lastM = m
            logState.lastC = c
            logState.lastMax = constraints.maxWidth
            logState.lastPath = path
            android.util.Log.d("pxmx-bezel", "S=$s T=$t E=$e M=$m C=$c max=${constraints.maxWidth} path=$path")
        }

        // 4. Hug when S <= C
        if (s <= c) {
            val hullMeasurables = subcompose(BezelSlot.HULL_HUG) {
                BezelHull(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    keysInLazyColumn = keysInLazyColumn,
                    coroutineScope = coroutineScope,
                    scrollState = null,
                )
            }
            val tightConstraints = Constraints(
                minWidth = s,
                maxWidth = s,
                minHeight = h,
                maxHeight = h,
            )
            val hullPlaceables = hullMeasurables.map { it.measure(tightConstraints) }
            val totalWidth = (s + e - m).coerceAtMost(constraints.maxWidth)
            layout(width = totalWidth, height = h) {
                hullPlaceables.forEach { it.placeRelative(0, 0) }
                editPlaceables.forEach { it.placeRelative(s - m, 0) }
            }
        } else {
            // 5. Overflow when S > C
            val hullMeasurables = subcompose(BezelSlot.HULL_OVERFLOW) {
                BezelHull(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    keysInLazyColumn = keysInLazyColumn,
                    coroutineScope = coroutineScope,
                    scrollState = scrollState,
                )
            }
            val tightConstraints = Constraints(
                minWidth = c,
                maxWidth = c,
                minHeight = h,
                maxHeight = h,
            )
            val hullPlaceables = hullMeasurables.map { it.measure(tightConstraints) }
            val totalWidth = (c + e - m).coerceAtMost(constraints.maxWidth)
            layout(width = totalWidth, height = h) {
                hullPlaceables.forEach { it.placeRelative(0, 0) }
                editPlaceables.forEach { it.placeRelative(c - m, 0) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("FunctionNaming")
private fun WeakTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullLabel: String = label,
    embedded: Boolean = false,
    armed: Boolean = false,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            bringIntoViewRequester.bringIntoView()
            delay(220)
            bringIntoViewRequester.bringIntoView()
        }
    }
    val shape = CutCornerShape(bottomEnd = if (selected) 16.dp else 10.dp)
    val border = when {
        selected || armed -> MaterialTheme.colorScheme.primary
        embedded -> TechColors.Divider
        else -> TechColors.Edge
    }
    val bg = when {
        selected -> TechColors.Deck
        embedded -> Color.Black
        else -> TechColors.Hull
    }
    val textColor = when {
        selected || armed -> MaterialTheme.colorScheme.primary
        embedded -> TechColors.Mute
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Armed keeps the short label so the row does not reflow under the finger.
    val textToDisplay = if (selected) fullLabel else label
    val letterSpacing = if (selected) 0.4.sp else 1.0.sp
    val horizontalPadding = if (selected) 12.dp else 10.dp

    Box(
        modifier = modifier
            .stupidChipFlip(selected)
            .bringIntoViewRequester(bringIntoViewRequester)
            .animateContentSize(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
            .then(if (selected) Modifier.neonSelectionHalo(enabled = true, color = border, shape = shape) else Modifier)
            .clip(shape)
            .border(1.dp, border, shape)
            .background(bg, shape)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = fullLabel
            }
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 7.dp)
            .defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = textToDisplay,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = letterSpacing,
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}


private fun editJoinPath(size: Size, cutPx: Float, bleedPx: Float = 0f): Path = Path().apply {
    moveTo(cutPx - bleedPx, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(-bleedPx, size.height)
    lineTo(cutPx - bleedPx, size.height - cutPx)
    close()
}

private fun editStrokePath(size: Size, cutPx: Float, strokePx: Float): Path = Path().apply {
    val halfStroke = strokePx / 2f
    moveTo(cutPx - halfStroke, halfStroke)
    lineTo(size.width - halfStroke, halfStroke)
    lineTo(size.width - halfStroke, size.height - halfStroke)
    lineTo(halfStroke, size.height - halfStroke)
    lineTo(cutPx - halfStroke, size.height - cutPx + halfStroke)
    close()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("FunctionNaming")
private fun GuestSectionPlate(
    title: String,
    subtitle: String? = null,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
    showPin: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    testTag: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val railColor = if (expanded) MaterialTheme.colorScheme.primary else TechColors.Edge
    TechPlate(
        railColor = railColor,
        showRail = true,
        testTag = testTag,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = if (showPin) onTogglePin else null,
                    ),
            ) {
                Text(
                    text = title.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showPin) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin from OPS" else "Pin to OPS",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            trailing?.invoke()
        }
        if (expanded) {
            TechDeck(
                modifier = Modifier.stupidSectionFold(expanded),
                showAccentBar = true,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun TechActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val shape = CutCornerShape(bottomEnd = 6.dp)
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(shape)
            .background(TechColors.Hull)
            .border(1.dp, if (enabled) TechColors.Edge else TechColors.Divider, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else TechColors.Mute,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun guestKeyToClipId(key: String): String = when (key) {
    "hero" -> "VITALS"
    "hardware" -> "HW"
    "network" -> "NET"
    "firewall" -> "FW"
    "snapshots" -> "SNAP"
    "backups" -> "BKP"
    "options" -> "OPT"
    "console" -> "CONSOLE"
    "history-graph" -> "HISTORY"
    "raw" -> "RAW"
    else -> key.uppercase()
}

@Composable
@Suppress("FunctionNaming", "LongParameterList")
private fun MovableDeckItem(
    key: String,
    index: Int,
    isEditing: Boolean,
    reduceMotion: Boolean,
    elapsedMs: () -> Long,
    onMoveUp: (String) -> Boolean,
    onMoveDown: (String) -> Boolean,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val clipId = remember(key) { guestKeyToClipId(key) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .deckCardWiggleDrag(
                isEditing = isEditing,
                index = index,
                clipId = clipId,
                elapsedMs = elapsedMs,
                reduceMotion = reduceMotion,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                haptic = haptic,
                enabled = true,
            ),
    ) {
        content()
    }
}

private const val DRAG_HOLD_MS = 240L
private const val DRAG_SLOP_DP = 12
private const val DRAG_THRESHOLD_DP = 48
private const val DRAG_SCALE_ARMED = 1.04f
private const val DRAG_SHADOW_ELEVATION = 8f

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
private fun Modifier.deckCardWiggleDrag(
    isEditing: Boolean,
    index: Int,
    clipId: String,
    elapsedMs: () -> Long,
    reduceMotion: Boolean,
    onMoveUp: (String) -> Boolean,
    onMoveDown: (String) -> Boolean,
    haptic: HapticFeedback,
    enabled: Boolean = true,
): Modifier {
    var isUnderFinger by remember { mutableStateOf(false) }
    var isArmed by remember { mutableStateOf(false) }
    var translationYPx by remember { mutableFloatStateOf(0f) }
    val layoutInRoot = remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Read during composition. A read that lives only inside graphicsLayer does not schedule the next frame.
    val elapsed = elapsedMs()
    val fingerDown = isUnderFinger
    val armedNow = isArmed
    val dragY = translationYPx
    if (!isEditing || !enabled) return this

    val rocking = if (fingerDown || armedNow) {
        0f
    } else {
        editWiggleDegrees(index, elapsed, reduceMotion)
    }
    val scale = if (armedNow && !reduceMotion) DRAG_SCALE_ARMED else 1f

    return this
        .zIndex(if (armedNow) 1f else 0f)
        .onGloballyPositioned { layoutInRoot.value = it }
        .pointerInput(clipId, isEditing) {
            fun fingerRootY(localY: Float): Float {
                val node = layoutInRoot.value
                val origin = if (node != null && node.isAttached) node.localToRoot(Offset.Zero).y else 0f
                return localY + origin
            }
            val slopPx = DRAG_SLOP_DP.dp.toPx()
            val thresholdPx = DRAG_THRESHOLD_DP.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                val startRealtime = SystemClock.uptimeMillis()
                val startUptime = down.uptimeMillis
                val startPos = down.position
                var lastRootY = fingerRootY(down.position.y)
                isUnderFinger = true
                var armed = false
                var accumulatedTranslationY = 0f

                try {
                    while (!armed) {
                        val heldSoFar = SystemClock.uptimeMillis() - startRealtime
                        val remaining = (DRAG_HOLD_MS - heldSoFar).coerceAtLeast(0L)
                        if (remaining <= 0L) {
                            armed = true
                            isArmed = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            break
                        }
                        val event = withTimeoutOrNull(remaining) {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                        if (event == null) {
                            armed = true
                            isArmed = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            break
                        }
                        val change = event.changes.find { it.id == pointerId }
                        if (change == null || !change.pressed) {
                            return@awaitEachGesture
                        }
                        val held = change.uptimeMillis - startUptime
                        val travel = (change.position - startPos).getDistance()
                        if (editDragShouldYieldToScroll(held, travel, slopPx)) {
                            return@awaitEachGesture
                        }
                        if (editDragShouldArm(held, travel, slopPx)) {
                            armed = true
                            isArmed = true
                            change.consume()
                            lastRootY = fingerRootY(change.position.y)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            break
                        }
                    }

                    if (armed) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.find { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                change?.consume()
                                break
                            }
                            change.consume()
                            val rootY = fingerRootY(change.position.y)
                            val dy = rootY - lastRootY
                            lastRootY = rootY
                            accumulatedTranslationY += dy
                            translationYPx = accumulatedTranslationY

                            var step = editDragStep(accumulatedTranslationY, thresholdPx)
                            while (step != 0) {
                                val moved = if (step == -1) onMoveUp(clipId) else onMoveDown(clipId)
                                if (moved) {
                                    if (step == -1) {
                                        accumulatedTranslationY += thresholdPx
                                    } else {
                                        accumulatedTranslationY -= thresholdPx
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    accumulatedTranslationY = if (step < 0) {
                                        -(thresholdPx - 1f)
                                    } else {
                                        thresholdPx - 1f
                                    }
                                }
                                translationYPx = accumulatedTranslationY
                                step = editDragStep(accumulatedTranslationY, thresholdPx)
                            }
                        }
                    }
                } finally {
                    isUnderFinger = false
                    isArmed = false
                    translationYPx = 0f
                }
            }
        }
        .graphicsLayer {
            rotationZ = rocking
            scaleX = scale
            scaleY = scale
            translationY = if (armedNow) dragY else 0f
            shadowElevation = if (armedNow) DRAG_SHADOW_ELEVATION else 0f
        }
}

@Composable
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "LongParameterList")
private fun EditBoardCard(
    board: OpsBoard,
    onMoveUp: (String) -> Boolean,
    onMoveDown: (String) -> Boolean,
    onTogglePin: (GuestSection) -> Unit,
    onToggleConsole: (Boolean) -> Unit,
    onToggleHistory: (Boolean) -> Unit,
    onSaveAsDefault: () -> Unit,
    onClose: () -> Unit,
    elapsedMs: () -> Long = { 0L },
    reduceMotion: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    TechPlate(
        railColor = MaterialTheme.colorScheme.primary,
        showRail = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OPS BOARD",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "PIN SECTIONS · REORDER DECK",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TechActionPlate(
                label = "DONE",
                onClick = onClose,
            )
        }
        TechDeck(showAccentBar = true) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "VITALS is locked first. RAW is locked last. Extra pinned sections sit above unpinned plates (max 4 pins).",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(Modifier.fillMaxWidth().height(1.dp).background(TechColors.Divider))

                board.clipOrder.forEachIndexed { index, clip ->
                    key(clip) {
                    val canMoveUp = index > 1 && clip != "VITALS" && clip != "RAW"
                    val canMoveDown = index < board.clipOrder.size - 2 && clip != "VITALS" && clip != "RAW"
                    val rowModifier = if (clip != "VITALS" && clip != "RAW") {
                        Modifier
                            .fillMaxWidth()
                            .deckCardWiggleDrag(
                                isEditing = true,
                                index = index,
                                clipId = clip,
                                elapsedMs = elapsedMs,
                                reduceMotion = reduceMotion,
                                onMoveUp = onMoveUp,
                                onMoveDown = onMoveDown,
                                haptic = haptic,
                                enabled = true,
                            )
                            .padding(vertical = 4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    }
                    when (clip) {
                        "VITALS" -> {
                            Row(
                                modifier = rowModifier,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("VITALS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("Hero & live metrics", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("[LOCKED FIRST]", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        "FACTS" -> {
                            Row(
                                modifier = rowModifier,
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("FACTS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    val unpinned = listOf("HW", "NET", "FW", "SNAP", "BKP", "OPT").filter { it !in board.pinnedSections }
                                    Text(unpinned.joinToString(" · "), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TechActionIcon(
                                    icon = Icons.Default.ArrowUpward,
                                    contentDescription = "Move up",
                                    enabled = canMoveUp,
                                    onClick = { onMoveUp("FACTS") },
                                )
                                TechActionIcon(
                                    icon = Icons.Default.ArrowDownward,
                                    contentDescription = "Move down",
                                    enabled = canMoveDown,
                                    onClick = { onMoveDown("FACTS") },
                                )
                            }
                        }
                        "CONSOLE" -> {
                            Row(
                                modifier = rowModifier,
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CONSOLE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text(if (board.includeConsole) "VISIBLE" else "HIDDEN", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = if (board.includeConsole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TechActionIcon(
                                    icon = Icons.Default.ArrowUpward,
                                    contentDescription = "Move up",
                                    enabled = canMoveUp && board.includeConsole,
                                    onClick = { onMoveUp("CONSOLE") },
                                )
                                TechActionIcon(
                                    icon = Icons.Default.ArrowDownward,
                                    contentDescription = "Move down",
                                    enabled = canMoveDown && board.includeConsole,
                                    onClick = { onMoveDown("CONSOLE") },
                                )
                                TechActionIcon(
                                    icon = if (board.includeConsole) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (board.includeConsole) "Hide console" else "Show console",
                                    onClick = { onToggleConsole(!board.includeConsole) },
                                )
                            }
                        }
                        "HISTORY" -> {
                            Row(
                                modifier = rowModifier,
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("HISTORY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text(if (board.includeHistory) "VISIBLE" else "HIDDEN", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = if (board.includeHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TechActionIcon(
                                    icon = Icons.Default.ArrowUpward,
                                    contentDescription = "Move up",
                                    enabled = canMoveUp && board.includeHistory,
                                    onClick = { onMoveUp("HISTORY") },
                                )
                                TechActionIcon(
                                    icon = Icons.Default.ArrowDownward,
                                    contentDescription = "Move down",
                                    enabled = canMoveDown && board.includeHistory,
                                    onClick = { onMoveDown("HISTORY") },
                                )
                                TechActionIcon(
                                    icon = if (board.includeHistory) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (board.includeHistory) "Hide history" else "Show history",
                                    onClick = { onToggleHistory(!board.includeHistory) },
                                )
                            }
                        }
                        "RAW" -> {
                            Row(
                                modifier = rowModifier,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("RAW", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("Raw guest config", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("[LOCKED LAST]", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> {
                            val sec = clipIdToSection(clip)
                            val label = sec?.title ?: clip
                            Row(
                                modifier = rowModifier,
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$label [$clip]", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("PINNED EXTRA", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                }
                                TechActionIcon(
                                    icon = Icons.Default.ArrowUpward,
                                    contentDescription = "Move up",
                                    enabled = canMoveUp,
                                    onClick = { onMoveUp(clip) },
                                )
                                TechActionIcon(
                                    icon = Icons.Default.ArrowDownward,
                                    contentDescription = "Move down",
                                    enabled = canMoveDown,
                                    onClick = { onMoveDown(clip) },
                                )
                                TechActionIcon(
                                    icon = Icons.Filled.PushPin,
                                    contentDescription = "Unpin from OPS",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = { sec?.let { onTogglePin(it) } },
                                )
                            }
                        }
                    }
                    }
                }

                val unpinnedSections = listOf(
                    GuestSection.HARDWARE,
                    GuestSection.NETWORK,
                    GuestSection.FIREWALL,
                    GuestSection.SNAPSHOTS,
                    GuestSection.BACKUPS,
                    GuestSection.OPTIONS,
                ).filter { !board.isPinned(it) }

                if (unpinnedSections.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TechColors.Divider))
                    Text(
                        text = "ADD PINNED SECTION (${board.pinnedSections.size}/4)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        unpinnedSections.forEach { sec ->
                            WeakTab(
                                label = sectionToClipId(sec),
                                selected = false,
                                onClick = { onTogglePin(sec) },
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(TechColors.Divider))
                TechActionPlate(
                    label = "USE AS DEFAULT FOR ALL GUESTS",
                    onClick = onSaveAsDefault,
                    modifier = Modifier.fillMaxWidth(),
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    name: String,
    status: String?,
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
    vmid: Long? = null,
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
) {
    TechPlate(railColor = techRailColor(status, busy)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val caption = if (vmid != null) "${kindLabel.uppercase()} · VMID $vmid" else kindLabel.uppercase()
                Text(
                    text = caption,
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
            val diskPct = formatDiskProgress(disk, maxdisk)
            MetricBar(
                label = "Disk",
                valueText = formatDiskUsage(disk, maxdisk),
                progress = diskPct,
            )

            Spacer(Modifier.height(6.dp))
            TechMetaLine("Uptime", formatUptime(uptime))

            Spacer(Modifier.height(8.dp))
            val tagList = TagUtils.splitTags(tags)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TAGS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(54.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    tagList.forEach { tag ->
                        TagChip(
                            tag = tag,
                            onRemove = { onRemoveTag(tag) },
                        )
                    }
                    AddTagButton(onAddTag = onAddTag)
                }
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
@Suppress("FunctionNaming")
private fun ConsoleCard(
    running: Boolean,
    busy: Boolean,
    guestType: GuestType,
    onOpenConsole: () -> Unit,
    isTokenSession: Boolean = false,
    still: ConsoleStill? = null,
) {
    TechPlate(
        railColor = if (running) TechColors.Amber else TechColors.Edge,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "CONSOLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (isTokenSession) {
                    Text(
                        text = "[API TOKEN]",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TechColors.Amber,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                !running -> {
                    // Guest stopped: disabled plate, never show stale frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CutCornerShape(bottomEnd = 10.dp))
                            .background(TechColors.Deck)
                            .border(1.dp, TechColors.Divider, CutCornerShape(bottomEnd = 10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Computer,
                                contentDescription = null,
                                tint = TechColors.Mute,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "CONSOLE (START GUEST FIRST)",
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.6.sp,
                                color = TechColors.Mute,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = if (guestType == GuestType.QEMU) "NOVNC · GUEST OFFLINE" else "TERMINAL · GUEST OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                guestType != GuestType.QEMU -> {
                    // LXC / Node: no still ever, live only
                    TechActionPlate(
                        label = "OPEN CONSOLE",
                        icon = Icons.Default.Computer,
                        onClick = onOpenConsole,
                        modifier = Modifier.fillMaxWidth(),
                        emphasized = true,
                    )
                    Text(
                        text = "TERMINAL · LIVE ONLY",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                still != null -> {
                    // QEMU running with active still: 16:10 box, dim still (alpha ~0.7), age caption, still tap = onOpenConsole
                    val bitmap = remember(still.jpegBytes) {
                        try {
                            val opts = android.graphics.BitmapFactory.Options().apply {
                                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                            }
                            android.graphics.BitmapFactory.decodeByteArray(still.jpegBytes, 0, still.jpegBytes.size, opts)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    val frameShape = CutCornerShape(bottomEnd = 16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(frameShape)
                            .background(Color.Black)
                            .border(1.dp, TechColors.Edge, frameShape)
                            .clickable(enabled = !busy, onClick = onOpenConsole),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Console frame ${still.ageFormatted()}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(alpha = 0.7f),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(CutCornerShape(bottomEnd = 4.dp))
                                .background(TechColors.Hull)
                                .border(1.dp, TechColors.Edge, CutCornerShape(bottomEnd = 4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = still.ageFormatted(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TechColors.Amber,
                            )
                        }
                        TechActionPlate(
                            label = "OPEN CONSOLE",
                            icon = Icons.Default.Computer,
                            onClick = onOpenConsole,
                            emphasized = true,
                        )
                    }
                    Text(
                        text = "NOVNC · LIVE FROM SERVER",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                else -> {
                    // QEMU running, no still yet: dim cut-corner 16:10 rectangle, caption NOVNC · NO FRAME YET, real OPEN CONSOLE plate inside
                    val frameShape = CutCornerShape(bottomEnd = 16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(frameShape)
                            .background(TechColors.Deck)
                            .border(1.dp, TechColors.Edge, frameShape)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "NOVNC · NO FRAME YET",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            TechActionPlate(
                                label = "OPEN CONSOLE",
                                icon = Icons.Default.Computer,
                                onClick = onOpenConsole,
                                emphasized = true,
                            )
                        }
                    }
                    Text(
                        text = "NOVNC · LIVE FROM SERVER",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (isTokenSession) {
                Text(
                    text = "API TOKEN SESSION · TICKET MAY BE REQUIRED BY PVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                    color = TechColors.Amber,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
