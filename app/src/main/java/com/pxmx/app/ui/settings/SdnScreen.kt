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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

    BackHandler(enabled = showApplyConfirm) {
        showApplyConfirm = false
    }
    BackHandler(enabled = !showApplyConfirm, onBack = onBack)

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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "SDN APPLY ERROR",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "[DISMISS]",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    err,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                // API Error Plate (501 SDN unused, 403, etc.)
                state.error?.let { err ->
                    val is501 = err.contains("501") || err.contains("Method not implemented", ignoreCase = true) || err.contains("not configured", ignoreCase = true)
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
                                    text = if (is501) "Software-Defined Networking is not configured or enabled on this cluster."
                                    else err,
                                    color = if (is501) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

                // Any SdnStatusInfo !isOk is an issue plate (not a silent empty list)
                if (state.issueStatuses.isNotEmpty()) {
                    item(key = "sdn-issues-plate") {
                        TechPlate(railColor = TechColors.Amber) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "SDN STATUS ISSUES / PENDING (${state.issueStatuses.size})",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TechColors.Amber,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(6.dp))
                                state.issueStatuses.forEach { issue ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${issue.name.uppercase()} (${issue.type ?: "zone"})",
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = (issue.status ?: "PENDING").uppercase(),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TechColors.Amber,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Zones Section
                item(key = "hdr-zones") {
                    TechSectionLabel("Zones", count = state.zones.size, accent = TechColors.CoolBlue)
                }

                if (state.zones.isEmpty()) {
                    item(key = "empty-zones") {
                        ZeroStateCard(title = "NO SDN ZONES", subtitle = "No SDN zones are defined in this cluster.")
                    }
                } else {
                    items(state.zones, key = { "z-${it.zone}" }) { zone ->
                        SdnZoneCard(zone)
                    }
                }

                // 2. Vnets Section
                item(key = "hdr-vnets") {
                    TechSectionLabel("Vnets", count = state.vnets.size, accent = TechColors.Amber)
                }

                if (state.vnets.isEmpty()) {
                    item(key = "empty-vnets") {
                        ZeroStateCard(title = "NO SDN VNETS", subtitle = "No SDN virtual networks are configured.")
                    }
                } else {
                    items(state.vnets, key = { "v-${it.vnet}" }) { vnet ->
                        SdnVnetCard(vnet)
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
                    items(state.statuses, key = { "s-${it.name}-${it.type}" }) { status ->
                        SdnStatusCard(status)
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun ZeroStateCard(title: String, subtitle: String) {
    TechPlate(railColor = TechColors.Edge) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TechColors.Mute,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SdnZoneCard(zone: SdnZoneInfo) {
    val typeUpper = zone.type?.uppercase() ?: "SIMPLE"
    TechPlate(railColor = TechColors.CoolBlue) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (zone.zone ?: "?").uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = TechColors.CoolBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TechColors.CoolBlue.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = typeUpper,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TechColors.CoolBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            val meta = listOfNotNull<String>(
                zone.ipam?.let { "IPAM $it" },
                zone.dns?.let { "DNS $it" },
                zone.mtu?.let { "MTU $it" },
                zone.bridge?.let { "BRIDGE $it" },
                zone.tag?.let { "TAG $it" },
                zone.peers?.let { "PEERS $it" },
            ).joinToString(" · ")

            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SdnVnetCard(vnet: SdnVnetInfo) {
    TechPlate(railColor = TechColors.Amber) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    (vnet.vnet ?: "?").uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                vnet.tag?.let { tag ->
                    Surface(
                        color = TechColors.Deck,
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        Text(
                            text = "TAG $tag",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            val meta = listOfNotNull<String>(
                vnet.zone?.let { "ZONE $it" },
                if (vnet.vlanaware) "VLAN-AWARE" else null,
                vnet.alias?.takeIf { it.isNotBlank() }?.let { "ALIAS $it" },
            ).joinToString(" · ")

            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SdnStatusCard(status: SdnStatusInfo) {
    val isOk = status.isOk
    val statusColor = if (isOk) TechColors.LinkGreen else TechColors.Danger
    val railColor = if (isOk) TechColors.LinkGreen else TechColors.Danger

    TechPlate(railColor = railColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.name.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    status.type?.takeIf { it.isNotBlank() }?.let { t ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· $t",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                status.controller?.takeIf { it.isNotBlank() }?.let { ctrl ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "CONTROLLER: $ctrl",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
            ) {
                Text(
                    text = (status.status ?: "UNKNOWN").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
