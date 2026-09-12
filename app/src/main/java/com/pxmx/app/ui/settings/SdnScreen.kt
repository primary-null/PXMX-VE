package com.pxmx.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.data.model.SdnStatusInfo
import com.pxmx.app.data.model.SdnVnetInfo
import com.pxmx.app.data.model.SdnZoneInfo
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.components.TechDeck
import com.pxmx.app.ui.components.TechMetaLine
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechSectionLabel
import com.pxmx.app.ui.components.TechStatusPlate
import com.pxmx.app.ui.components.techTopAppBarColors
import com.pxmx.app.ui.guest.detail.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdnScreen(
    viewModel: SdnViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showApplyConfirm by remember { mutableStateOf(false) }
    var showAddZone by remember { mutableStateOf(false) }
    var showAddVnet by remember { mutableStateOf(false) }
    var zoneToDelete by remember { mutableStateOf<String?>(null) }
    var vnetToDelete by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showApplyConfirm || showAddZone || showAddVnet || zoneToDelete != null || vnetToDelete != null) {
        showApplyConfirm = false
        showAddZone = false
        showAddVnet = false
        zoneToDelete = null
        vnetToDelete = null
    }
    BackHandler(enabled = !(showApplyConfirm || showAddZone || showAddVnet || zoneToDelete != null || vnetToDelete != null), onBack = onBack)

    if (showApplyConfirm) {
        ConfirmDialog(
            title = "Apply SDN Configuration",
            body = "Apply pending SDN configuration across the cluster? This will reload networking configuration on all cluster nodes.",
            confirm = "APPLY",
            onDismiss = { showApplyConfirm = false },
            onConfirm = {
                showApplyConfirm = false
                viewModel.applySdn()
            },
        )
    }

    if (showAddZone) {
        var zoneId by remember { mutableStateOf("") }
        var zoneType by remember { mutableStateOf("simple") }
        AlertDialog(
            onDismissRequest = { showAddZone = false },
            title = { Text("Add Zone") },
            text = {
                Column {
                    OutlinedTextField(
                        value = zoneId,
                        onValueChange = { zoneId = it },
                        label = { Text("Zone ID") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = zoneType,
                        onValueChange = { zoneType = it },
                        label = { Text("Type") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddZone = false
                    if (zoneId.isNotBlank()) {
                        viewModel.createZone(zoneId, zoneType)
                    }
                }) { Text("ADD") }
            },
            dismissButton = {
                TextButton(onClick = { showAddZone = false }) { Text("CANCEL") }
            }
        )
    }

    if (showAddVnet) {
        var vnetId by remember { mutableStateOf("") }
        var vnetZone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddVnet = false },
            title = { Text("Add VNet") },
            text = {
                Column {
                    OutlinedTextField(
                        value = vnetId,
                        onValueChange = { vnetId = it },
                        label = { Text("VNet ID") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vnetZone,
                        onValueChange = { vnetZone = it },
                        label = { Text("Zone ID") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddVnet = false
                    if (vnetId.isNotBlank() && vnetZone.isNotBlank()) {
                        viewModel.createVnet(vnetId, vnetZone)
                    }
                }) { Text("ADD") }
            },
            dismissButton = {
                TextButton(onClick = { showAddVnet = false }) { Text("CANCEL") }
            }
        )
    }

    zoneToDelete?.let { zoneId ->
        ConfirmDialog(
            title = "Delete Zone",
            body = "Are you sure you want to delete zone '$zoneId'?",
            confirm = "DELETE",
            onDismiss = { zoneToDelete = null },
            onConfirm = {
                zoneToDelete = null
                viewModel.deleteZone(zoneId)
            }
        )
    }

    vnetToDelete?.let { vnetId ->
        ConfirmDialog(
            title = "Delete VNet",
            body = "Are you sure you want to delete vnet '$vnetId'?",
            confirm = "DELETE",
            onDismiss = { vnetToDelete = null },
            onConfirm = {
                vnetToDelete = null
                viewModel.deleteVnet(vnetId)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = techTopAppBarColors(),
                title = {
                    Column {
                        Text("SDN")
                        Text(
                            "ZONES · VNETS · STATUS",
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
                    TextButton(
                        onClick = { showApplyConfirm = true },
                        enabled = !state.loading && !state.isApplying,
                    ) {
                        Text(
                            "APPLY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (!state.loading && !state.isApplying) TechColors.Amber else TechColors.Mute,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading && !state.isApplying,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.zones.isEmpty() && state.vnets.isEmpty() && state.statuses.isEmpty() && state.error == null) {
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
                // Applying Job Plate
                if (state.isApplying) {
                    item(key = "sdn-applying-plate") {
                        TechPlate(railColor = TechColors.Amber) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = TechColors.Amber,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        state.jobStatus ?: "APPLYING SDN CONFIGURATION",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = TechColors.Amber,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                                if (state.taskLogLines.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    TechDeck {
                                        Column(Modifier.padding(8.dp)) {
                                            state.taskLogLines.takeLast(6).forEach { line ->
                                                Text(
                                                    text = line,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Error Plate
                state.actionError?.let { err ->
                    item(key = "sdn-action-error-plate") {
                        TechPlate(railColor = MaterialTheme.colorScheme.error) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.clearActionError() }
                                    .padding(14.dp),
                            ) {
                                Text(
                                    text = "SDN TASK FAILED",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = err,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                // Initial load error
                state.error?.let { err ->
                    val is501 = err.contains("501") || err.contains("not implemented", ignoreCase = true)
                    val is403 = err.contains("403") || err.contains("permission check failed", ignoreCase = true)
                    item(key = "error-item") {
                        TechPlate(railColor = if (is501) TechColors.Mute else MaterialTheme.colorScheme.error) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    text = when {
                                        is501 -> "SDN NOT CONFIGURED (501)"
                                        is403 -> "PERMISSION DENIED (403)"
                                        else -> "SDN API ERROR"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (is501) TechColors.Mute else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (is501) "Software-Defined Networking is not configured or enabled on this cluster." else err,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                // Top level issues
                if (state.issueStatuses.isNotEmpty()) {
                    item(key = "sdn-issues-plate") {
                        TechPlate(railColor = TechColors.Amber) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    text = "SDN CONTROLLER ISSUES",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TechColors.Amber,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                state.issueStatuses.forEach { sdnStatus ->
                                    TechMetaLine(label = sdnStatus.name, value = sdnStatus.status ?: "unknown")
                                }
                            }
                        }
                    }
                }

                // 1. Zones Section
                item(key = "hdr-zones") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TechSectionLabel("Zones", count = state.zones.size, accent = TechColors.CoolBlue)
                        IconButton(onClick = { showAddZone = true }, enabled = !state.isApplying) {
                            Icon(Icons.Default.Add, contentDescription = "Add Zone", tint = TechColors.CoolBlue)
                        }
                    }
                }
                if (state.zones.isEmpty()) {
                    item(key = "empty-zones") {
                        ZeroStateCard(title = "NO SDN ZONES", subtitle = "No SDN zones are defined in this cluster.")
                    }
                } else {
                    items(state.zones, key = { it.zone ?: it.hashCode() }) { zone ->
                        TechPlate(
                            railColor = TechColors.CoolBlue,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = (zone.zone ?: "unknown").uppercase(),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        zone.type?.let { type ->
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = TechColors.CoolBlue.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.border(1.dp, TechColors.CoolBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                                            ) {
                                                Text(
                                                    text = type.uppercase(),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TechColors.CoolBlue,
                                                )
                                            }
                                        }
                                    }
                                    if (zone.ipam != null || zone.peers != null) {
                                        Spacer(Modifier.height(4.dp))
                                        val m = mutableListOf<String>()
                                        zone.ipam?.let { m.add("IPAM $it") }
                                        zone.peers?.let { m.add("PEERS $it") }
                                        Text(
                                            text = m.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { zoneToDelete = zone.zone }, enabled = !state.isApplying && zone.zone != null) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Zone", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // 2. Vnets Section
                item(key = "hdr-vnets") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TechSectionLabel("Vnets", count = state.vnets.size, accent = TechColors.Amber)
                        IconButton(onClick = { showAddVnet = true }, enabled = !state.isApplying) {
                            Icon(Icons.Default.Add, contentDescription = "Add VNet", tint = TechColors.Amber)
                        }
                    }
                }
                if (state.vnets.isEmpty()) {
                    item(key = "empty-vnets") {
                        ZeroStateCard(title = "NO SDN VNETS", subtitle = "No SDN virtual networks are configured.")
                    }
                } else {
                    items(state.vnets, key = { it.vnet ?: it.hashCode() }) { vnet ->
                        TechPlate(
                            railColor = TechColors.Amber,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = (vnet.vnet ?: "unknown").uppercase(),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val details = buildString {
                                        vnet.zone?.let { append("ZONE $it") }
                                        vnet.alias?.let { append(" · ALIAS $it") }
                                        vnet.tag?.let { append(" · VLAN $it") }
                                        vnet.vlanaware.takeIf { it }?.let { append(" · VLAN-AWARE") }
                                    }
                                    if (details.isNotEmpty()) {
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { vnetToDelete = vnet.vnet }, enabled = !state.isApplying && vnet.vnet != null) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete VNet", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // 3. Status Section
                item(key = "hdr-status") {
                    TechSectionLabel("Zone Status", count = state.statuses.size, accent = TechColors.LinkGreen)
                }
                if (state.statuses.isEmpty()) {
                    item(key = "empty-status") {
                        ZeroStateCard(title = "NO STATUS REPORTED", subtitle = "No live SDN controller/zone status reported.")
                    }
                } else {
                    items(state.statuses, key = { "${it.name}-${it.type}" }) { statusInfo ->
                        TechPlate(
                            railColor = if (statusInfo.isOk) TechColors.LinkGreen else MaterialTheme.colorScheme.error,
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = statusInfo.name.uppercase(),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    statusInfo.type?.let { t ->
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "[$t]",
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                val meta = buildString {
                                    append("STATUS ")
                                    append(statusInfo.status?.uppercase() ?: "UNKNOWN")
                                    statusInfo.controller?.let { c ->
                                        append(" · CTL $c")
                                    }
                                }
                                Text(
                                    text = meta,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (statusInfo.isOk) TechColors.LinkGreen else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZeroStateCard(title: String, subtitle: String) {
    TechPlate(railColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
            .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

