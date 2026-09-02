package com.pxmx.app.ui.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.pxmx.app.ui.components.SystemLogStrip
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ui.util.AppToast
import com.pxmx.app.data.model.ClusterResource
import com.pxmx.app.data.model.GuestAction
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.ThemeMode
import com.pxmx.app.ui.components.TechActionPlate
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechStatusPlate
import com.pxmx.app.ui.guest.detail.CreateBackupDialog
import com.pxmx.app.ui.guest.detail.availableActions
import com.pxmx.app.ui.icons.GuestIcons
import com.pxmx.app.ui.util.formatBytes
import com.pxmx.app.ui.util.formatEpoch
import com.pxmx.app.ui.util.formatPercent
import com.pxmx.app.ui.util.formatUptime
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenGuest: (node: String, type: String, vmid: Long, name: String) -> Unit,
    onOpenStorage: (node: String, storage: String) -> Unit,
    onOpenNode: (node: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenLogs: () -> Unit = {},
    onOpenServers: () -> Unit,
    onOpenUpdates: () -> Unit = {},
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val latestLog by viewModel.latestLog.collectAsStateWithLifecycle()
    val version = state.session?.version?.display ?: "…"
    val host = state.session?.config?.displayHost ?: ""
    val site = state.site
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val app = context.applicationContext as? com.pxmx.app.ProxmoxApp
        if (app != null) {
            com.pxmx.app.ui.tour.TourController.startTourIfEligible(app.sessionStore)
        }
    }
    LaunchedEffect(state.session?.version?.major) {
        val major = state.session?.version?.major
        if (major != null && !viewModel.hasShownVersionToast()) {
            when (major) {
                9 -> AppToast.VERSION_PVE9.show(context)
                8 -> AppToast.VERSION_PVE8.show(context)
                else -> AppToast.VERSION_UNSUPPORTED.show(context)
            }
            viewModel.markVersionToastShown()
        }
    }

    val siteTitle = site?.title ?: site?.nodeName ?: "Proxmox"
    val expandedSubtitle = buildString {
        append(siteTitle)
        append(" · ")
        append(host)
        append(" · PVE ")
        append(version)
        site?.let {
            if (it.isCluster && it.nodeCount > 1) {
                append(" · ")
                append(it.nodeCount)
                append(" nodes")
            } else {
                append(" · ")
                append(it.subtitleKind)
            }
        }
    }
    val collapsedSubtitle = "$siteTitle · PVE $version"
    val listState = rememberLazyListState()
    val collapseProgress = if (listState.firstVisibleItemIndex == 0) {
        (listState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f)
    } else {
        1f
    }
    val wordmarkFontSize = (36f - 14f * collapseProgress).sp

    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val collapsed = rememberSaveable { mutableStateOf(setOf<String>()) }
    var backupTarget by remember { mutableStateOf<ClusterResource?>(null) }
    var backupStorages by remember { mutableStateOf<List<String>>(emptyList()) }
    var backupToDeviceMode by remember { mutableStateOf(false) }

    // Back handling ordering: modal dialogs/menus close first
    BackHandler(enabled = menuOpen) {
        menuOpen = false
    }
    BackHandler(enabled = backupTarget != null) {
        backupTarget = null
        backupToDeviceMode = false
    }
    BackHandler(enabled = state.showDeployDialog) {
        viewModel.showDeployDialog(false)
    }
    BackHandler(enabled = state.showThemePicker) {
        viewModel.showThemePicker(false)
    }
    BackHandler(enabled = state.showAccounts) {
        viewModel.showAccounts(false)
    }

    val visibleRows by remember(state.listRows, collapsed.value) {
        derivedStateOf {
            val list = mutableListOf<Pair<HomeListRow, Int>>()
            var currentSectionCollapsed = false
            var batchIndex = 0
            for (row in state.listRows) {
                if (row is HomeListRow.Section) {
                    currentSectionCollapsed = collapsed.value.contains(row.title)
                    batchIndex = 0
                    list.add(row to 0)
                } else if (!currentSectionCollapsed) {
                    list.add(row to batchIndex++)
                }
            }
            list
        }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenServers),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "PXMX",
                                fontSize = wordmarkFontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp,
                            )
                            if (host.substringBefore(':') == "demo") {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    onClick = { Toast.makeText(context, "Simulated data — no real server", Toast.LENGTH_SHORT).show() }
                                ) {
                                    Text(
                                        text = "DEMO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (collapseProgress > 0.5f) collapsedSubtitle else expandedSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable(onClick = onOpenServers)
                        )
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        TechActionPlate(
                            label = "Sync",
                            onClick = { viewModel.refresh() },
                        )
                        TechActionPlate(
                            label = "Tasks",
                            onClick = { onOpenTasks() },
                        )
                        TechActionPlate(
                            label = "Menu",
                            onClick = {
                                menuOpen = true
                                com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.MENU_BUTTON)
                            },
                            emphasized = menuOpen,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Sort block — options depend on the active tab (Guests / Storage / …)
                        Text(
                            text = "SORT · ${state.filter.label.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        ResourceSort.optionsFor(state.filter).forEach { mode ->
                            val selected = state.activeSort == mode
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            mode.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            mode.menuHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        sortIcon(mode),
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingIcon = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setSort(mode)
                                },
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Updates") },
                            leadingIcon = { Icon(Icons.Default.SystemUpdate, null) },
                            onClick = {
                                menuOpen = false
                                onOpenUpdates()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser") },
                            leadingIcon = { Icon(Icons.Default.Language, null) },
                            onClick = {
                                menuOpen = false
                                val url = state.session?.config?.let { "https://${it.displayHost}" } ?: return@DropdownMenuItem
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Appearance") },
                            leadingIcon = { Icon(Icons.Default.DarkMode, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.showThemePicker(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Deploy from template") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.showDeployDialog(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("All servers") },
                            leadingIcon = { Icon(Icons.Default.Storage, null) },
                            onClick = {
                                menuOpen = false
                                onOpenServers()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Saved connections") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.showAccounts(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Switch account") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.logout()
                                onSwitchAccount()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Category rail — same chrome language as cards, embedded under the top bar.
            CategoryRail(
                selected = state.filter,
                onSelect = { viewModel.setFilter(it) },
            )

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        "Search name · node · vmid · tag",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = TechColors.Edge,
                    focusedContainerColor = TechColors.Hull,
                    unfocusedContainerColor = TechColors.Hull,
                ),
            )

            state.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.resources.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (visibleRows.isEmpty() && !state.loading) {
                                item {
                                    Text(
                                        "No resources",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp),
                                    )
                                }
                            }
                            items(
                                visibleRows,
                                key = { (row, _) ->
                                    when (row) {
                                        is HomeListRow.Section -> "sec-${row.title}-${row.count}"
                                        is HomeListRow.Item ->
                                            row.resource.id
                                                ?: "${row.resource.type}-${row.resource.vmid}-${row.resource.node}"
                                    }
                                },
                            ) { (row, batchIndex) ->
                                val stagger = min(batchIndex * 28, 224)
                                Box(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(220, delayMillis = stagger),
                                        fadeOutSpec = tween(160),
                                        placementSpec = tween(220)
                                    )
                                ) {
                                    when (row) {
                                        is HomeListRow.Section -> StatusSectionHeader(
                                            title = row.title,
                                            count = row.count,
                                            collapsed = collapsed.value.contains(row.title),
                                            onToggle = {
                                                collapsed.value = if (collapsed.value.contains(row.title)) {
                                                    collapsed.value - row.title
                                                } else {
                                                    collapsed.value + row.title
                                                }
                                            }
                                        )
                                        is HomeListRow.Item -> {
                                            val res = row.resource
                                            val id = res.id.orEmpty()
                                            ResourceCard(
                                                resource = res,
                                                busy = id in state.busyGuestIds,
                                                onClick = {
                                                    when {
                                                        res.isGuest && res.node != null && res.vmid != null && res.type != null -> {
                                                            com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.GUEST_CARD)
                                                            onOpenGuest(res.node, res.type, res.vmid, res.displayName)
                                                        }
                                                        res.type == "storage" && res.node != null -> {
                                                            val name = res.storage ?: res.name ?: return@ResourceCard
                                                            onOpenStorage(res.node, name)
                                                        }
                                                        res.type == "node" && res.node != null ->
                                                            onOpenNode(res.node)
                                                    }
                                                },
                                                onPowerToggle = { on ->
                                                    viewModel.quickPowerToggle(res, on)
                                                },
                                                onGuestAction = { action ->
                                                    viewModel.triggerGuestAction(res, action)
                                                },
                                                onBackupOnServer = {
                                                    scope.launch {
                                                        backupStorages = viewModel.getNodeStorage(res.node ?: "")
                                                        backupTarget = res
                                                        backupToDeviceMode = false
                                                    }
                                                },
                                                onBackupToDevice = {
                                                    scope.launch {
                                                        backupStorages = viewModel.getNodeStorage(res.node ?: "")
                                                        backupTarget = res
                                                        backupToDeviceMode = true
                                                    }
                                                },
                                                onReboot = { viewModel.quickReboot(res) },
                                                onOnbootToggle = { on ->
                                                    viewModel.quickOnbootToggle(res, on)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            // Scroll past the list → Settings drawer (tools hub).
                            item(key = "settings-footer") {
                                Spacer(Modifier.height(8.dp))
                                SettingsFooter(onClick = onOpenSettings)
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showThemePicker) {
        AlertDialog(
            onDismissRequest = { viewModel.showThemePicker(false) },
            title = { Text("Appearance") },
            text = {
                Column {
                    Text(
                        "OLED Dark uses pure black for AMOLED panels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                            )
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showThemePicker(false) }) { Text("Done") }
            },
        )
    }

    if (state.showAccounts) {
        AlertDialog(
            onDismissRequest = { viewModel.showAccounts(false) },
            title = { Text("Saved connections") },
            text = {
                Column {
                    if (profiles.isEmpty()) {
                        Text("No saved profiles yet.")
                    } else {
                        profiles.forEach { p ->
                            Text(p.displayLabel, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${p.displayUser} · ${p.secretMask}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (p.lastUsedEpochMs > 0) {
                                Text(
                                    "Last used ${formatEpoch(p.lastUsedEpochMs / 1000)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    Text(
                        "Passwords/tokens are never displayed. Switch account to pick another profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.showAccounts(false)
                    viewModel.logout()
                    onLogout()
                }) { Text("Switch…") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAccounts(false) }) { Text("Close") }
            },
        )
    }

    if (state.showDeployDialog) {
        val templates = state.resources.filter { it.template == 1 }
        val maxVmid = state.resources.maxOfOrNull { it.vmid ?: 0L } ?: 100L
        DeployDialog(
            templates = templates,
            maxVmid = maxVmid,
            deploying = state.deploying,
            error = state.error,
            onDismiss = { viewModel.showDeployDialog(false) },
            onConfirm = { source, newId, name ->
                viewModel.triggerDeploy(source, newId, name)
            }
        )
    }

    backupTarget?.let { target ->
        CreateBackupDialog(
            storages = backupStorages,
            onDismiss = { backupTarget = null },
            onConfirm = { storage, mode ->
                if (backupToDeviceMode) {
                    viewModel.triggerBackupToDevice(target, storage)
                } else {
                    viewModel.triggerBackup(target, storage, mode)
                }
                backupTarget = null
            }
        )
    }
}

@Composable
private fun SettingsFooter(onClick: () -> Unit) {
    TechPlate(
        railColor = TechColors.Edge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                "SETTINGS",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Network, updates, and app tools",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryRail(
    selected: ResourceFilter,
    onSelect: (ResourceFilter) -> Unit,
) {
    val entries = ResourceFilter.entries
    val last = entries.lastIndex
    // Outer hull; selected segment gets its own paper-cut so highlight matches the language.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEachIndexed { index, filter ->
            val isOn = selected == filter
            val isLast = index == last
            val shape = when {
                isOn && isLast -> CutCornerShape(bottomEnd = 14.dp)
                isOn -> CutCornerShape(bottomEnd = 10.dp)
                isLast -> CutCornerShape(bottomEnd = 10.dp)
                else -> RectangleShape
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isOn) TechColors.Deck else TechColors.Hull)
                    .border(
                        1.dp,
                        if (isOn) MaterialTheme.colorScheme.primary else TechColors.Edge,
                        shape,
                    )
                    .clickable { onSelect(filter) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = filter.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (isOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .width(if (isOn) 28.dp else 10.dp)
                        .height(2.dp)
                        .background(
                            if (isOn) MaterialTheme.colorScheme.primary
                            else TechColors.Edge,
                        ),
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun sortIcon(mode: ResourceSort): ImageVector = when (mode) {
    ResourceSort.DEFAULT -> Icons.Default.Sort
    ResourceSort.USAGE -> Icons.Default.Speed
    ResourceSort.RAM -> Icons.Default.Memory
    ResourceSort.DISK, ResourceSort.CAPACITY, ResourceSort.USED, ResourceSort.FREE ->
        Icons.Default.Storage
    ResourceSort.NAME -> Icons.Default.Sort
}

@Composable
private fun StatusSectionHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    val accent = when (title) {
        "RUNNING" -> TechColors.LinkGreen
        "PAUSED / SUSPENDED" -> TechColors.Amber
        "STOPPED" -> TechColors.Mute
        "TEMPLATES" -> TechColors.Mute
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -180f else 0f,
        animationSpec = tween(180),
        label = "chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp).rotate(rotation)
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(3.dp)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = accent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(accent.copy(alpha = 0.35f)),
        )
    }
}

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

@Composable
private fun ResourceCard(
    resource: ClusterResource,
    busy: Boolean = false,
    onClick: () -> Unit,
    onPowerToggle: (Boolean) -> Unit = {},
    onGuestAction: (GuestAction) -> Unit = {},
    onBackupOnServer: () -> Unit = {},
    onBackupToDevice: () -> Unit = {},
    onReboot: () -> Unit = {},
    onOnbootToggle: (Boolean) -> Unit = {},
) {
    val clickable = resource.isGuest || resource.type == "storage" || resource.type == "node"
    val iconStyle = remember(resource.id, resource.ostype, resource.name, resource.type, resource.tags) {
        GuestIcons.styleFor(resource)
    }
    val isGuest = resource.isGuest
    val showQuick = isGuest && resource.template != 1
    val accent = iconTint(resource, iconStyle.accent)
    val railColor by animateColorAsState(
        targetValue = when {
            busy -> TechColors.LinkGreen.copy(alpha = 0.55f)
            resource.isRunning -> TechColors.LinkGreen
            resource.isPausedOrSuspended -> TechColors.Amber
            resource.status == "stopped" || resource.status == "offline" -> TechColors.StoppedRail
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(280),
        label = "rail",
    )

    // Communicator plate: hard edges, paper-cut on bottom-right only.
    val plateShape = CutCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomEnd = 28.dp,
        bottomStart = 0.dp,
    )
    val hull = TechColors.Hull
    val deck = TechColors.Deck
    val edge = TechColors.Edge

    val isStorage = resource.type == "storage"
    val isNode = resource.type == "node"
    val (showFill, usageFraction) = when {
        isStorage && resource.maxdisk != null && resource.maxdisk > 0 && resource.disk != null -> {
            true to (resource.disk.toDouble() / resource.maxdisk.toDouble()).coerceIn(0.0, 1.0)
        }
        isNode && resource.maxmem != null && resource.maxmem > 0 && resource.mem != null -> {
            true to (resource.mem.toDouble() / resource.maxmem.toDouble()).coerceIn(0.0, 1.0)
        }
        else -> false to 0.0
    }
    val animatedUsage by animateFloatAsState(
        targetValue = if (showFill) usageFraction.toFloat() else 0f,
        animationSpec = tween(800),
        label = "resourceFillUsage",
    )
    val fillBaseColor = when {
        usageFraction < 0.6 -> TechColors.LinkGreen
        usageFraction < 0.85 -> TechColors.Amber
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(plateShape)
            .background(hull)
            .border(1.dp, edge, plateShape),
    ) {
        if (showFill) {
            ResourceFillBackground(
                animatedUsage = animatedUsage,
                fillColor = fillBaseColor,
                modifier = Modifier.matchParentSize(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Status spine — LCARS-ish vertical rail
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(railColor),
            )
            Column(modifier = Modifier.weight(1f)) {
                // Identity bay (dark)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (clickable) {
                                Modifier.clickable(onClick = onClick)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isGuest) 44.dp else 36.dp)
                            .background(accent.copy(alpha = 0.12f), RectangleShape)
                            .border(1.dp, accent.copy(alpha = 0.45f), RectangleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = iconStyle.icon,
                            contentDescription = iconStyle.label,
                            tint = accent,
                            modifier = Modifier.size(if (isGuest) 26.dp else 22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = resource.displayName.uppercase(),
                                style = if (isGuest) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            resource.vmid?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "VMID $it",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.4.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = subtitle(resource, iconStyle.label).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val metrics = metricsLine(resource)
                        if (metrics != "—") {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = metrics,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (isGuest) {
                        var backupMenu by remember { mutableStateOf(false) }
                        Box {
                            TechActionPlate(
                                label = "BACKUP",
                                onClick = { backupMenu = true },
                                icon = Icons.Default.SaveAlt,
                                emphasized = backupMenu
                            )
                            DropdownMenu(
                                expanded = backupMenu,
                                onDismissRequest = { backupMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Back up on server") },
                                    leadingIcon = { Icon(Icons.Default.Storage, null) },
                                    onClick = {
                                        backupMenu = false
                                        onBackupOnServer()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Back up to device") },
                                    leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                                    onClick = {
                                        backupMenu = false
                                        onBackupToDevice()
                                    }
                                )
                            }
                        }
                    } else {
                        TechStatusPlate(status = resource.status)
                    }
                }

                if (showQuick) {
                    // Raised control deck — INVERTED: lighter gray over dark hull
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(deck)
                            .border(width = 0.dp, color = Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        // Thin top accent bar like a console bezel
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            )
                            Spacer(Modifier.height(8.dp))
                            GuestQuickActions(
                                resource = resource,
                                busy = busy,
                                onPowerToggle = onPowerToggle,
                                onGuestAction = onGuestAction,
                                onReboot = onReboot,
                                onOnbootToggle = onOnbootToggle,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestQuickActions(
    resource: ClusterResource,
    busy: Boolean,
    onPowerToggle: (Boolean) -> Unit,
    onGuestAction: (GuestAction) -> Unit,
    onReboot: () -> Unit,
    onOnbootToggle: (Boolean) -> Unit,
) {
    val running = resource.isRunning
    val powerScale by animateFloatAsState(
        targetValue = if (running) 1.04f else 1f,
        animationSpec = tween(180),
        label = "powerScale",
    )
    val divider = TechColors.Divider
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Flat PWR label: tap toggles stop/start, long-press opens the power menu.
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 44.dp)
                    .scale(powerScale)
                    .combinedClickable(
                        enabled = !busy,
                        onClick = {
                            com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                            onPowerToggle(!running)
                        },
                        onLongClick = {
                            com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                            menuOpen = true
                        },
                        onLongClickLabel = "Power menu"
                    )
                    .semantics { contentDescription = "Power" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "PWR",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                val guestType = GuestType.fromResourceType(resource.type) ?: GuestType.QEMU
                val actions = availableActions(guestType, running, resource.status)

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = {
                        menuOpen = false
                        com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                    }
                ) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                menuOpen = false
                                com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                                onGuestAction(action)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (action) {
                                        GuestAction.START -> Icons.Default.PlayArrow
                                        GuestAction.SHUTDOWN -> Icons.Default.PowerSettingsNew
                                        GuestAction.STOP -> Icons.Default.Stop
                                        GuestAction.REBOOT -> Icons.Default.RestartAlt
                                        GuestAction.SUSPEND -> Icons.Default.Pause
                                        GuestAction.RESUME -> Icons.Default.PlayArrow
                                        GuestAction.RESET -> Icons.Default.Refresh
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    if (resource.onboot != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Auto-start on boot") },
                            trailingIcon = {
                                Checkbox(
                                    checked = resource.onboot == 1,
                                    onCheckedChange = null
                                )
                            },
                            onClick = {
                                menuOpen = false
                                com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.PWR_BUTTON)
                                com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.AUTO_BUTTON)
                                onOnbootToggle(resource.onboot != 1)
                            }
                        )
                    }
                }
            }
        }

        Box(Modifier.width(1.dp).height(40.dp).background(divider))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Flat RST label: 44dp touch target.
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 44.dp)
                    .clickable(enabled = running && !busy, onClick = onReboot)
                    .semantics { contentDescription = "Reboot" },
                contentAlignment = Alignment.Center,
            ) {
                if (busy && running) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "RST",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }

        Box(Modifier.width(1.dp).height(40.dp).background(divider))

        val onbootOn = resource.onboot == 1
        val onbootKnown = resource.onboot != null
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val autoColor = if (onbootOn) MaterialTheme.colorScheme.primary else Color(0xFF757575)
            Surface(
                color = autoColor.copy(alpha = 0.08f),
                contentColor = autoColor,
                shape = RoundedCornerShape(1.dp),
                modifier = Modifier
                    .height(28.dp)
                    .clickable(enabled = onbootKnown && !busy) {
                        com.pxmx.app.ui.tour.TourController.advance(com.pxmx.app.ui.tour.TourStep.AUTO_BUTTON)
                        onOnbootToggle(!onbootOn)
                    }
                    .clip(RoundedCornerShape(1.dp))
                    .border(1.dp, autoColor.copy(alpha = if (onbootOn) 0.7f else 0.3f), RoundedCornerShape(1.dp))
                    .semantics { contentDescription = "Auto-start on host boot" }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(autoColor, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "AUTO",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = if (onbootKnown) autoColor else autoColor.copy(alpha = 0.4f),
                    )
                }
            }
        }

        Box(Modifier.width(1.dp).height(40.dp).background(divider))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val statusColor by animateColorAsState(
                targetValue = when {
                    busy -> Color(0xFF69F0AE).copy(alpha = 0.6f)
                    running -> Color(0xFF69F0AE) // on → green
                    resource.isPausedOrSuspended -> Color(0xFFFF9800) // suspended → orange
                    resource.status == "stopped" || resource.status == "offline" -> Color(0xFF757575)
                    else -> MaterialTheme.colorScheme.error
                },
                animationSpec = tween(250),
                label = "statusColor",
            )
            val statusLabel = when (resource.status?.lowercase()) {
                "running" -> "RUNNING"
                "paused" -> "PAUSED"
                "suspended" -> "SUSPENDED"
                "frozen" -> "FROZEN"
                "stopped", "offline" -> "STOPPED"
                else -> if (busy) "WORKING" else "UNKNOWN"
            }
            Surface(
                color = statusColor.copy(alpha = 0.08f),
                contentColor = statusColor,
                shape = RoundedCornerShape(1.dp),
                modifier = Modifier
                    .height(28.dp)
                    .border(1.dp, statusColor.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(statusColor, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = statusColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun iconTint(resource: ClusterResource, accent: Color): Color {
    return when (resource.status?.lowercase()) {
        "running", "online", "available" -> accent
        "paused", "suspended", "frozen", "prelaunch" -> Color(0xFFFF9800)
        "stopped", "offline", "disabled" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        "unknown" -> MaterialTheme.colorScheme.error
        else -> accent.copy(alpha = 0.75f)
    }
}

private fun subtitle(r: ClusterResource, kindLabel: String? = null): String = buildString {
    when (r.type) {
        "storage" -> {
            append(r.plugintype ?: "storage")
            r.node?.let { append(" · ").append(it) }
            r.content?.takeIf { it.isNotBlank() }?.let {
                append(" · ").append(it.replace(',', '/'))
            }
        }
        "qemu", "lxc" -> {
            append(kindLabel ?: r.type?.uppercase() ?: "?")
            r.node?.let { append(" · ").append(it) }
            r.hastate?.takeIf { it.isNotBlank() }?.let { append(" · HA:").append(it) }
            r.template?.takeIf { it == 1 }?.let { append(" · template") }
        }
        else -> {
            append(r.type?.uppercase() ?: "?")
            r.node?.let { append(" · ").append(it) }
        }
    }
}

private fun metricsLine(r: ClusterResource): String {
    val parts = mutableListOf<String>()
    when (r.type) {
        "storage" -> {
            if (r.disk != null || r.maxdisk != null) {
                parts += "${formatBytes(r.disk)} / ${formatBytes(r.maxdisk)}"
            }
            // used fraction when both sides present
            val used = r.disk
            val total = r.maxdisk
            if (used != null && total != null && total > 0) {
                parts += formatPercent(used.toDouble() / total.toDouble() * 100.0)
            }
        }
        "node" -> {
            r.cpuPercent?.let { parts += "CPU ${formatPercent(it)}" }
            r.memPercent?.let { parts += "MEM ${formatPercent(it)}" }
            if (r.disk != null || r.maxdisk != null) {
                parts += "DISK ${formatBytes(r.disk)} / ${formatBytes(r.maxdisk)}"
            }
            r.uptime?.takeIf { it > 0 }?.let { parts += "up ${formatUptime(it)}" }
        }
        else -> {
            r.cpuPercent?.let { parts += "CPU ${formatPercent(it)}" }
            r.memPercent?.let { parts += "MEM ${formatPercent(it)}" }
            r.vcpuCount?.takeIf { r.isGuest }?.let { parts += "${it} vCPU" }
            r.uptime?.takeIf { it > 0 }?.let { parts += "up ${formatUptime(it)}" }
        }
    }
    return parts.joinToString("  ·  ").ifBlank { "—" }
}
